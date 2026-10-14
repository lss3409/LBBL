package org.levimc.launcher.util;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** v0.4.26：手写 SMTP 客户端（无第三方依赖）——日志上报直发邮件。
 *  走 SSL 直连（QQ 465），AUTH LOGIN 认证，multipart/mixed 带 zip 附件。
 *  参考：用户密码轮转工具（smtp.qq.com 587/SSL + AUTH PLAIN）。 */
public final class SmtpSender {

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 20000;

    private SmtpSender() {
    }

    public interface Callback {
        void onSuccess();
        void onFailure(String error);
    }

    /** 发送带附件的邮件。附件为 null 时纯文本正文。 */
    public static void send(String host, int port, String user, String authCode,
                            String from, String[] to, String subject, String bodyText,
                            File attachment, Callback callback) {
        new Thread(() -> {
            try {
                sendSync(host, port, user, authCode, from, to, subject, bodyText, attachment);
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onFailure(String.valueOf(e.getMessage()));
                }
            }
        }, "smtp-sender").start();
    }

    private static void sendSync(String host, int port, String user, String authCode,
                                 String from, String[] to, String subject, String bodyText,
                                 File attachment) throws Exception {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = (SSLSocket) factory.createSocket();
        socket.connect(new java.net.InetSocketAddress(host, port), CONNECT_TIMEOUT);
        socket.setSoTimeout(READ_TIMEOUT);
        socket.startHandshake();

        BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        OutputStream out = socket.getOutputStream();

        expect(in, "220");
        command(in, out, "EHLO lbbl\r\n", "250");
        // AUTH LOGIN：账号与授权码均为 base64
        command(in, out, "AUTH LOGIN\r\n", "334");
        command(in, out, Base64.encodeToString(user.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP) + "\r\n", "334");
        command(in, out, Base64.encodeToString(authCode.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP) + "\r\n", "235");
        command(in, out, "MAIL FROM:<" + from + ">\r\n", "250");
        for (String t : to) {
            command(in, out, "RCPT TO:<" + t + ">\r\n", "250");
        }
        command(in, out, "DATA\r\n", "354");
        out.write(buildMime(from, to, subject, bodyText, attachment).getBytes(StandardCharsets.UTF_8));
        out.write("\r\n.\r\n".getBytes(StandardCharsets.UTF_8));
        out.flush();
        expect(in, "250");
        try {
            out.write("QUIT\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception ignored) {
        }
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }

    /** 发一条命令并等指定前缀的响应。 */
    private static void command(BufferedReader in, OutputStream out, String cmd, String code)
            throws IOException {
        out.write(cmd.getBytes(StandardCharsets.UTF_8));
        out.flush();
        expect(in, code);
    }

    /** 读多行响应，直到以 code+空格 开头的行（3 位响应码）。 */
    private static void expect(BufferedReader in, String code) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (line.length() >= 3 && line.startsWith(code) && line.charAt(3) != '-') {
                return;
            }
            if (line.length() >= 3 && !line.startsWith(code)) {
                // 4xx/5xx 错误——把整行当错误抛出
                if (line.charAt(0) == '4' || line.charAt(0) == '5') {
                    throw new IOException(line);
                }
            }
        }
        throw new IOException("Connection closed");
    }

    private static String buildMime(String from, String[] to, String subject,
                                    String bodyText, File attachment) throws IOException {
        String boundary = "LBBLBoundary" + System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        sb.append("From: ").append(encodeHeaderFrom(from)).append("\r\n");
        sb.append("To: ").append(to[0]).append("\r\n");
        sb.append("Subject: ").append(encodeHeader(subject)).append("\r\n");
        sb.append("MIME-Version: 1.0\r\n");
        sb.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n");
        sb.append("\r\n");
        // 正文（UTF-8 base64）
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Type: text/plain; charset=UTF-8\r\n");
        sb.append("Content-Transfer-Encoding: base64\r\n\r\n");
        sb.append(wrap76(Base64.encodeToString(
                (bodyText == null ? "" : bodyText).getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP))).append("\r\n");
        // 附件 zip
        if (attachment != null && attachment.exists()) {
            sb.append("--").append(boundary).append("\r\n");
            sb.append("Content-Type: application/zip; name=\"").append(attachment.getName()).append("\"\r\n");
            sb.append("Content-Transfer-Encoding: base64\r\n");
            sb.append("Content-Disposition: attachment; filename=\"").append(attachment.getName()).append("\"\r\n\r\n");
            sb.append(wrap76(Base64.encodeToString(readAll(attachment), Base64.NO_WRAP))).append("\r\n");
        }
        sb.append("--").append(boundary).append("--\r\n");
        return sb.toString();
    }

    private static byte[] readAll(File f) throws IOException {
        try (InputStream is = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    private static String wrap76(String base64) {
        StringBuilder sb = new StringBuilder(base64.length() + base64.length() / 76 + 4);
        for (int i = 0; i < base64.length(); i += 76) {
            int end = Math.min(i + 76, base64.length());
            sb.append(base64, i, end).append("\r\n");
        }
        return sb.toString().trim();
    }

    /** 邮件头 UTF-8 编码（非 ASCII 时用 =?UTF-8?B?...?=）。 */
    private static String encodeHeader(String text) {
        boolean ascii = true;
        for (char c : text.toCharArray()) {
            if (c > 127) {
                ascii = false;
                break;
            }
        }
        if (ascii) {
            return text;
        }
        return "=?UTF-8?B?" + Base64.encodeToString(text.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP) + "?=";
    }

    /** From 头（发件人邮箱可能带中文名，仅邮箱时直接返回）。 */
    private static String encodeHeaderFrom(String from) {
        return from;
    }
}
