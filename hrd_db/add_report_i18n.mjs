// v0.4.73：日志上报系列翻译补全（9 个外语）
import fs from 'fs';

const T = {
  'values-es': ['Describe qué pasó (opcional)', 'Reportar registros', 'Los registros se envían al correo del desarrollador por defecto (o se suben a logshare.cn si se elige).', 'Enviar por correo', 'Subir a la web', 'Enviar', 'Describe qué pasó (opcional). Tu descripción se sube junto con los registros.', 'Subiendo registros…', 'Registros subidos ✓', 'Comparte este enlace con el desarrollador o en la comunidad:', 'Subida fallida', 'Error al subir: %1$s', 'Copiar enlace', 'Compartir enlace', 'Enviar por app de correo', 'Recopilando registros…', 'Registros empaquetados. Elige una app para enviarlos.', 'Error al recopilar registros: %1$s', 'No se encontraron archivos de registro', 'No hay app de correo disponible'],
  'values-fr': ['Décrivez ce qui est arrivé (facultatif)', 'Signaler les journaux', "Les journaux sont envoyés à l'e-mail du développeur par défaut (ou téléversés sur logshare.cn).", 'Envoyer par e-mail', 'Téléverser sur le site', 'Envoyer', 'Décrivez ce qui est arrivé (facultatif). Votre description est téléversée avec les journaux.', 'Téléversement des journaux…', 'Journaux téléversés ✓', 'Partagez ce lien avec le développeur ou la communauté :', 'Échec du téléversement', 'Échec du téléversement : %1$s', 'Copier le lien', 'Partager le lien', 'Envoyer via une app de messagerie', 'Collecte des journaux…', 'Journaux empaquetés. Choisissez une app pour les envoyer.', 'Échec de la collecte des journaux : %1$s', 'Aucun fichier journal trouvé', 'Aucune app de messagerie disponible'],
  'values-hi': ['क्या हुआ, बताएं (वैकल्पिक)', 'लॉग रिपोर्ट करें', 'लॉग डिफ़ॉल्ट रूप से डेवलपर के ईमेल पर भेजे जाते हैं (या चुने जाने पर logshare.cn पर अपलोड होते हैं)।', 'ईमेल से भेजें', 'वेबसाइट पर अपलोड करें', 'भेजें', 'क्या हुआ, बताएं (वैकल्पिक)। आपका विवरण लॉग के साथ अपलोड होता है।', 'लॉग अपलोड हो रहे हैं…', 'लॉग अपलोड ✓', 'यह लिंक डेवलपर या समुदाय के साथ साझा करें:', 'अपलोड विफल', 'अपलोड विफल: %1$s', 'लिंक कॉपी करें', 'लिंक साझा करें', 'ईमेल ऐप से भेजें', 'लॉग एकत्र हो रहे हैं…', 'लॉग पैक हो गए। भेजने के लिए एक ऐप चुनें।', 'लॉग एकत्र करने में विफल: %1$s', 'कोई लॉग फ़ाइल नहीं मिली', 'कोई ईमेल ऐप उपलब्ध नहीं'],
  'values-idn': ['Jelaskan yang terjadi (opsional)', 'Laporkan Log', 'Log dikirim ke email pengembang secara bawaan (atau diunggah ke logshare.cn jika dipilih).', 'Kirim ke email', 'Unggah ke situs web', 'Kirim', 'Jelaskan yang terjadi (opsional). Deskripsi Anda diunggah bersama log.', 'Mengunggah log…', 'Log terunggah ✓', 'Bagikan tautan ini dengan pengembang atau komunitas:', 'Unggahan Gagal', 'Gagal mengunggah: %1$s', 'Salin Tautan', 'Bagikan Tautan', 'Kirim lewat aplikasi email', 'Mengumpulkan log…', 'Log terpaket. Pilih aplikasi untuk mengirimnya.', 'Gagal mengumpulkan log: %1$s', 'Tidak ada file log', 'Tidak ada aplikasi email'],
  'values-ja': ['何が起きたか記述（任意）', 'ログを報告', 'ログは既定で開発者のメールへ送信されます（選択時は logshare.cn にアップロード）。', 'メールで送信', 'ウェブサイトにアップロード', '送信', '何が起きたか記述（任意）。説明はログと一緒にアップロードされます。', 'ログをアップロード中…', 'ログをアップロードしました ✓', 'このリンクを開発者やコミュニティと共有してください：', 'アップロード失敗', 'アップロードに失敗しました：%1$s', 'リンクをコピー', 'リンクを共有', 'メールアプリで送信', 'ログを収集中…', 'ログをパッケージしました。送信するアプリを選んでください。', 'ログの収集に失敗しました：%1$s', 'ログファイルが見つかりません', 'メールアプリがありません'],
  'values-pt': ['Descreva o que aconteceu (opcional)', 'Reportar Logs', 'Os logs são enviados ao e-mail do desenvolvedor por padrão (ou para logshare.cn se escolhido).', 'Enviar por e-mail', 'Enviar para o site', 'Enviar', 'Descreva o que aconteceu (opcional). Sua descrição é enviada junto com os logs.', 'Enviando logs…', 'Logs enviados ✓', 'Compartilhe este link com o desenvolvedor ou a comunidade:', 'Falha no Envio', 'Falha ao enviar: %1$s', 'Copiar Link', 'Compartilhar Link', 'Enviar pelo app de e-mail', 'Coletando logs…', 'Logs empacotados. Escolha um app para enviá-los.', 'Falha ao coletar logs: %1$s', 'Nenhum arquivo de log encontrado', 'Nenhum app de e-mail disponível'],
  'values-ru': ['Опишите, что произошло (необязательно)', 'Отправить журналы', 'Журналы по умолчанию отправляются на почту разработчика (или загружаются на logshare.cn).', 'Отправить на почту', 'Загрузить на сайт', 'Отправить', 'Опишите, что произошло (необязательно). Описание загружается вместе с журналами.', 'Загрузка журналов…', 'Журналы загружены ✓', 'Поделитесь ссылкой с разработчиком или сообществом:', 'Ошибка загрузки', 'Не удалось загрузить: %1$s', 'Копировать ссылку', 'Поделиться ссылкой', 'Отправить через почтовое приложение', 'Сбор журналов…', 'Журналы упакованы. Выберите приложение для отправки.', 'Не удалось собрать журналы: %1$s', 'Файлы журналов не найдены', 'Нет почтового приложения'],
  'values-tr': ['Ne olduğunu açıklayın (isteğe bağlı)', 'Günlükleri Bildir', 'Günlükler varsayılan olarak geliştiricinin e-postasına gönderilir (seçilirse logshare.cn sitesine yüklenir).', 'E-postayla gönder', 'Web sitesine yükle', 'Gönder', 'Ne olduğunu açıklayın (isteğe bağlı). Açıklamanız günlüklerle birlikte yüklenir.', 'Günlükler yükleniyor…', 'Günlükler yüklendi ✓', 'Bu bağlantıyı geliştiriciyle veya toplulukla paylaşın:', 'Yükleme Başarısız', 'Yüklenemedi: %1$s', 'Bağlantıyı Kopyala', 'Bağlantıyı Paylaş', 'E-posta uygulamasıyla gönder', 'Günlükler toplanıyor…', 'Günlükler paketlendi. Göndermek için bir uygulama seçin.', 'Günlükler toplanamadı: %1$s', 'Günlük dosyası bulunamadı', 'E-posta uygulaması yok'],
  'values-vi': ['Mô tả điều đã xảy ra (tùy chọn)', 'Báo cáo Nhật ký', 'Nhật ký được gửi đến email của nhà phát triển theo mặc định (hoặc tải lên logshare.cn nếu chọn).', 'Gửi qua email', 'Tải lên trang web', 'Gửi', 'Mô tả điều đã xảy ra (tùy chọn). Mô tả của bạn được tải lên cùng nhật ký.', 'Đang tải nhật ký lên…', 'Đã tải nhật ký lên ✓', 'Chia sẻ liên kết này với nhà phát triển hoặc cộng đồng:', 'Tải lên thất bại', 'Không tải lên được: %1$s', 'Sao chép Liên kết', 'Chia sẻ Liên kết', 'Gửi qua ứng dụng email', 'Đang thu thập nhật ký…', 'Đã đóng gói nhật ký. Chọn một ứng dụng để gửi.', 'Không thu thập được nhật ký: %1$s', 'Không tìm thấy tệp nhật ký', 'Không có ứng dụng email'],
};

const keys = [
  'report_crash_desc_hint', 'report_logs', 'report_logs_desc', 'report_method_mail',
  'report_method_site', 'report_send', 'report_desc_sub', 'report_uploading',
  'report_upload_success', 'report_upload_success_desc', 'report_upload_failed_title',
  'report_upload_failed', 'report_copy_link', 'report_share_link',
  'report_send_email_fallback', 'report_logs_collecting', 'report_logs_done',
  'report_logs_failed', 'report_logs_empty', 'report_logs_no_app',
];

const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
// 撇号转义（Android 字符串资源要求；已有值的语言文件都带反斜杠）
const escApos = s => s.replace(/'/g, "\\'");

let count = 0;
for (const [lang, vals] of Object.entries(T)) {
  const f = `app/src/main/res/${lang}/strings.xml`;
  let c = fs.readFileSync(f, 'utf8');
  let block = '\n    <!-- v0.4.73：日志上报系列翻译补全 -->';
  keys.forEach((k, i) => {
    block += `\n    <string name="${k}">${escApos(esc(vals[i]))}</string>`;
  });
  c = c.replace('</resources>', block + '\n</resources>');
  fs.writeFileSync(f, c);
  count++;
}
console.log('updated', count, 'lang files');
