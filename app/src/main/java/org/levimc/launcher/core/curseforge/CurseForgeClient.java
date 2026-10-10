package org.levimc.launcher.core.curseforge;

import org.levimc.launcher.BuildConfig;
import com.google.gson.Gson;

import org.levimc.launcher.core.curseforge.models.ContentSearchResponse;
import org.levimc.launcher.core.curseforge.models.ModFilesResponse;
import org.levimc.launcher.core.curseforge.models.StringResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CurseForgeClient {
    private static final String TAG = "CurseForgeClient";
    private static final String BASE_URL = "https://api.curseforge.com";
    private static final String API_KEY = BuildConfig.CURSEFORGE_API_KEY;

    public static final int GAME_ID_MINECRAFT = 78022;

    // v0.4.8：排序对齐官方 GDK 版（CurseForgePage.tsx sortOptions）
    public static final String SORT_FEATURED = "1";
    public static final String SORT_POPULARITY = "2";
    public static final String SORT_LAST_UPDATED = "3";
    public static final String SORT_TOTAL_DOWNLOADS = "6";
    public static final String SORT_CREATION_DATE = "10";

    private final OkHttpClient client;
    private final Gson gson;

    private static CurseForgeClient instance;

    private CurseForgeClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    public static synchronized CurseForgeClient getInstance() {
        if (instance == null) {
            instance = new CurseForgeClient();
        }
        return instance;
    }

    public interface CurseForgeCallback<T> {
        void onSuccess(T result);
        void onError(Throwable t);
    }

    /** v0.4.8：MC 版本号列表（GDK 版 GetCurseForgeGameVersions 同款）。 */
    public void getGameVersions(CurseForgeCallback<java.util.List<String>> callback) {
        String url = BASE_URL + "/v1/games/" + GAME_ID_MINECRAFT + "/versions";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("x-api-key", API_KEY)
                .addHeader("Accept", "application/json")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("Unexpected code " + response));
                    return;
                }
                try {
                    String json = response.body().string();
                    models.GameVersionsResponse r = gson.fromJson(json,
                            models.GameVersionsResponse.class);
                    java.util.List<String> names = new java.util.ArrayList<>();
                    if (r != null && r.data != null) {
                        for (models.GameVersionsResponse.GameVersion v : r.data) {
                            if (v.name != null) {
                                names.add(v.name);
                            }
                        }
                    }
                    callback.onSuccess(names);
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    /** v0.4.8：分类树（GDK 版 GetCurseForgeCategories 同款）：isClass=大类，
     *  其余为分类，分类跟随所选大类联动。 */
    public void getCategories(CurseForgeCallback<java.util.List<models.CategoriesResponse.CategoryData>> callback) {
        HttpUrl url = HttpUrl.parse(BASE_URL + "/v1/categories").newBuilder()
                .addQueryParameter("gameId", String.valueOf(GAME_ID_MINECRAFT))
                .build();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("x-api-key", API_KEY)
                .addHeader("Accept", "application/json")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("Unexpected code " + response));
                    return;
                }
                try {
                    String json = response.body().string();
                    models.CategoriesResponse r = gson.fromJson(json,
                            models.CategoriesResponse.class);
                    callback.onSuccess(r != null && r.data != null ? r.data
                            : java.util.Collections.emptyList());
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    public void searchContent(String query, int classId, String version, int index, int pageSize, String sortField, String sortOrder, CurseForgeCallback<ContentSearchResponse> callback) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "/v1/mods/search").newBuilder();
        urlBuilder.addQueryParameter("gameId", String.valueOf(GAME_ID_MINECRAFT));
        urlBuilder.addQueryParameter("sortField", sortField != null ? sortField : SORT_POPULARITY);
        urlBuilder.addQueryParameter("sortOrder", sortOrder != null ? sortOrder : "desc");
        urlBuilder.addQueryParameter("index", String.valueOf(index));
        urlBuilder.addQueryParameter("pageSize", String.valueOf(pageSize));
        
        if (query != null && !query.isEmpty()) {
            urlBuilder.addQueryParameter("searchFilter", query);
        }
        if (classId > 0) {
            urlBuilder.addQueryParameter("classId", String.valueOf(classId));
        }
        if (version != null && !version.isEmpty() && !"All".equals(version)) {
            urlBuilder.addQueryParameter("gameVersion", version);
        }

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .addHeader("x-api-key", API_KEY)
                .addHeader("Accept", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("Unexpected code " + response));
                    return;
                }
                
                try {
                    String json = response.body().string();
                    ContentSearchResponse contentResponse = gson.fromJson(json, ContentSearchResponse.class);
                    callback.onSuccess(contentResponse);
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    public void getContentDescription(int contentId, CurseForgeCallback<String> callback) {
        String url = BASE_URL + "/v1/mods/" + contentId + "/description";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("x-api-key", API_KEY)
                .addHeader("Accept", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("Unexpected code " + response));
                    return;
                }

                try {
                    String json = response.body().string();
                    StringResponse stringResponse = gson.fromJson(json, StringResponse.class);
                    callback.onSuccess(stringResponse.data);
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    public void getModFiles(int modId, int index, int pageSize, CurseForgeCallback<ModFilesResponse> callback) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "/v1/mods/" + modId + "/files").newBuilder();
        urlBuilder.addQueryParameter("index", String.valueOf(index));
        urlBuilder.addQueryParameter("pageSize", String.valueOf(pageSize));

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .addHeader("x-api-key", API_KEY)
                .addHeader("Accept", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("Unexpected code " + response));
                    return;
                }

                try {
                    String json = response.body().string();
                    ModFilesResponse filesResponse = gson.fromJson(json, ModFilesResponse.class);
                    callback.onSuccess(filesResponse);
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }
}
