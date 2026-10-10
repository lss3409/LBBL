package org.levimc.launcher.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.levimc.launcher.R;
import org.levimc.launcher.core.curseforge.CurseForgeClient;
import org.levimc.launcher.core.curseforge.models.CategoriesResponse;
import org.levimc.launcher.core.curseforge.models.Content;
import org.levimc.launcher.core.curseforge.models.ContentSearchResponse;
import org.levimc.launcher.ui.adapter.CurseForgeContentAdapter;
import org.levimc.launcher.util.UIHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * v0.4.8：布局对齐官方 GDK 版 CurseForgePage——搜索行 + 横向过滤器行
 * （MC 版本 / 资源类型 / 分类 / 排序），分类跟随资源类型联动。
 */
public class CurseForgeActivity extends BaseActivity {

    private EditText searchBox;
    private Spinner spinnerVersion;
    private Spinner spinnerClass;
    private Spinner spinnerCategory;
    private Spinner spinnerSort;
    private RecyclerView recyclerView;
    private ProgressBar loadingProgress;
    private CurseForgeContentAdapter adapter;
    private CurseForgeClient client;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int currentPage = 1;
    private int totalPages = 1;
    private static final int PAGE_SIZE = 20;

    /** 版本列表（API 拉取；失败用兜底常见版本）。 */
    private final List<String> gameVersions = new ArrayList<>();
    /** 分类树（isClass=资源类型大类；其余为分类）。 */
    private final List<CategoriesResponse.CategoryData> allCategories = new ArrayList<>();

    private static class SortOption {
        final String name;
        final String field;
        SortOption(String name, String field) {
            this.name = name;
            this.field = field;
        }
        @Override
        public String toString() {
            return name;
        }
    }

    private final List<SortOption> sortOptions = new ArrayList<>();
    private SortOption currentSort;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_curseforge);

        client = CurseForgeClient.getInstance();

        setupSortOptions();
        initViews();
        loadFilterData();
        loadContent();
    }

    /** 排序对齐官方 GDK 版：Featured/Popularity/Last Updated/Creation Date/Total Downloads。 */
    private void setupSortOptions() {
        sortOptions.add(new SortOption(getString(R.string.curseforge_featured), CurseForgeClient.SORT_FEATURED));
        sortOptions.add(new SortOption(getString(R.string.curseforge_relevancy), CurseForgeClient.SORT_POPULARITY));
        sortOptions.add(new SortOption(getString(R.string.curseforge_last_updated), CurseForgeClient.SORT_LAST_UPDATED));
        sortOptions.add(new SortOption(getString(R.string.curseforge_creation_date), CurseForgeClient.SORT_CREATION_DATE));
        sortOptions.add(new SortOption(getString(R.string.curseforge_total_downloads), CurseForgeClient.SORT_TOTAL_DOWNLOADS));
        currentSort = sortOptions.get(1); // 官方默认 selectedSort=1（Featured）
    }

    private void initViews() {
        searchBox = findViewById(R.id.search_box);
        spinnerVersion = findViewById(R.id.spinner_version);
        spinnerClass = findViewById(R.id.spinner_class);
        spinnerCategory = findViewById(R.id.spinner_category);
        spinnerSort = findViewById(R.id.spinner_sort);
        recyclerView = findViewById(R.id.mods_recycler);
        loadingProgress = findViewById(R.id.loading_progress);

        adapter = new CurseForgeContentAdapter(this::onContentClick, new CurseForgeContentAdapter.OnPageChangeListener() {
            @Override
            public void onNextPage() {
                if (currentPage < totalPages) {
                    currentPage++;
                    loadContent();
                }
            }

            @Override
            public void onPrevPage() {
                if (currentPage > 1) {
                    currentPage--;
                    loadContent();
                }
            }

            @Override
            public void onJumpToPage(int page) {
                EditText input = new EditText(CurseForgeActivity.this);
                input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                input.setHint("1 - " + totalPages);
                input.setTextColor(getColor(R.color.on_surface));
                input.setHintTextColor(getColor(R.color.text_secondary));
                new org.levimc.launcher.ui.dialogs.CustomAlertDialog(CurseForgeActivity.this)
                        .setTitleText(getString(R.string.jump_to_page))
                        .setCustomView(input)
                        .setPositiveButton(getString(R.string.go), v -> {
                            String s = input.getText().toString().trim();
                            if (s.isEmpty()) {
                                return;
                            }
                            try {
                                int target = Integer.parseInt(s);
                                if (target >= 1 && target <= totalPages && target != currentPage) {
                                    currentPage = target;
                                    loadContent();
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        })
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show();
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        searchBox.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                currentPage = 1;
                loadContent();
                UIHelper.hideKeyboard(this);
                return true;
            }
            return false;
        });
        findViewById(R.id.btn_search).setOnClickListener(v -> {
            currentPage = 1;
            loadContent();
            UIHelper.hideKeyboard(this);
        });

        spinnerSort.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, sortOptions));
        spinnerSort.setSelection(1);
        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= sortOptions.size()) {
                    return;
                }
                if (sortOptions.get(position).equals(currentSort)) {
                    return; // 首次绑定不重载
                }
                currentSort = sortOptions.get(position);
                currentPage = 1;
                loadContent();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /** 拉取版本列表与分类树（失败用兜底）。 */
    private void loadFilterData() {
        client.getGameVersions(new CurseForgeClient.CurseForgeCallback<List<String>>() {
            @Override
            public void onSuccess(List<String> result) {
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    gameVersions.clear();
                    gameVersions.addAll(result);
                    bindVersionSpinner();
                });
            }

            @Override
            public void onError(Throwable t) {
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    // 兜底常见基岩版版本
                    gameVersions.clear();
                    gameVersions.add("1.21.80");
                    gameVersions.add("1.21.70");
                    gameVersions.add("1.21.60");
                    gameVersions.add("1.21.50");
                    gameVersions.add("1.21.40");
                    gameVersions.add("1.21.30");
                    gameVersions.add("1.21.20");
                    gameVersions.add("1.21.10");
                    gameVersions.add("1.21.0");
                    gameVersions.add("1.20.80");
                    bindVersionSpinner();
                });
            }
        });

        client.getCategories(new CurseForgeClient.CurseForgeCallback<List<CategoriesResponse.CategoryData>>() {
            @Override
            public void onSuccess(List<CategoriesResponse.CategoryData> result) {
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    allCategories.clear();
                    allCategories.addAll(result);
                    bindClassSpinner();
                });
            }

            @Override
            public void onError(Throwable t) {
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    // 兜底常见大类（CurseForge 基岩版）
                    allCategories.clear();
                    CategoriesResponse.CategoryData addons = new CategoriesResponse.CategoryData();
                    addons.id = 4984;
                    addons.name = getString(R.string.curseforge_addons);
                    addons.isClass = true;
                    allCategories.add(addons);
                    CategoriesResponse.CategoryData maps = new CategoriesResponse.CategoryData();
                    maps.id = 6913;
                    maps.name = getString(R.string.curseforge_maps);
                    maps.isClass = true;
                    allCategories.add(maps);
                    CategoriesResponse.CategoryData skins = new CategoriesResponse.CategoryData();
                    skins.id = 6925;
                    skins.name = getString(R.string.curseforge_skins);
                    skins.isClass = true;
                    allCategories.add(skins);
                    CategoriesResponse.CategoryData tex = new CategoriesResponse.CategoryData();
                    tex.id = 6929;
                    tex.name = getString(R.string.curseforge_texture_packs);
                    tex.isClass = true;
                    allCategories.add(tex);
                    CategoriesResponse.CategoryData scripts = new CategoriesResponse.CategoryData();
                    scripts.id = 6940;
                    scripts.name = getString(R.string.curseforge_scripts);
                    scripts.isClass = true;
                    allCategories.add(scripts);
                    bindClassSpinner();
                });
            }
        });
    }

    private void bindVersionSpinner() {
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.curseforge_all_versions));
        items.addAll(gameVersions);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVersion.setAdapter(adapter);
        spinnerVersion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    currentPage = 1;
                    loadContent();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void bindClassSpinner() {
        List<CategoriesResponse.CategoryData> classes = new ArrayList<>();
        for (CategoriesResponse.CategoryData c : allCategories) {
            if (c.isClass) {
                classes.add(c);
            }
        }
        classes.sort((a, b) -> Integer.compare(a.displayIndex, b.displayIndex));
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.curseforge_all_classes));
        for (CategoriesResponse.CategoryData c : classes) {
            items.add(c.name);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerClass.setAdapter(adapter);
        spinnerClass.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                bindCategorySpinner();
                currentPage = 1;
                loadContent();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void bindCategorySpinner() {
        int classPos = spinnerClass.getSelectedItemPosition();
        int classId = 0;
        if (classPos > 0) {
            List<CategoriesResponse.CategoryData> classes = new ArrayList<>();
            for (CategoriesResponse.CategoryData c : allCategories) {
                if (c.isClass) {
                    classes.add(c);
                }
            }
            classes.sort((a, b) -> Integer.compare(a.displayIndex, b.displayIndex));
            if (classPos - 1 < classes.size()) {
                classId = classes.get(classPos - 1).id;
            }
        }
        List<CategoriesResponse.CategoryData> cats = new ArrayList<>();
        for (CategoriesResponse.CategoryData c : allCategories) {
            if (!c.isClass && (classId == 0 || c.classId == classId)) {
                cats.add(c);
            }
        }
        cats.sort((a, b) -> Integer.compare(a.displayIndex, b.displayIndex));
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.curseforge_all_categories));
        for (CategoriesResponse.CategoryData c : cats) {
            items.add(c.name);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);
        spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    currentPage = 1;
                    loadContent();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /** 当前选中分类 id（无选择 = 0，仅传 classId）。 */
    private int selectedCategoryId() {
        int pos = spinnerCategory.getSelectedItemPosition();
        if (pos <= 0) {
            return 0;
        }
        int classPos = spinnerClass.getSelectedItemPosition();
        int classId = 0;
        if (classPos > 0) {
            List<CategoriesResponse.CategoryData> classes = new ArrayList<>();
            for (CategoriesResponse.CategoryData c : allCategories) {
                if (c.isClass) {
                    classes.add(c);
                }
            }
            classes.sort((a, b) -> Integer.compare(a.displayIndex, b.displayIndex));
            if (classPos - 1 < classes.size()) {
                classId = classes.get(classPos - 1).id;
            }
        }
        List<CategoriesResponse.CategoryData> cats = new ArrayList<>();
        for (CategoriesResponse.CategoryData c : allCategories) {
            if (!c.isClass && (classId == 0 || c.classId == classId)) {
                cats.add(c);
            }
        }
        cats.sort((a, b) -> Integer.compare(a.displayIndex, b.displayIndex));
        return pos - 1 < cats.size() ? cats.get(pos - 1).id : 0;
    }

    private void loadContent() {
        String query = searchBox.getText().toString();

        int classPos = spinnerClass.getSelectedItemPosition();
        int classId = 0;
        if (classPos > 0) {
            List<CategoriesResponse.CategoryData> classes = new ArrayList<>();
            for (CategoriesResponse.CategoryData c : allCategories) {
                if (c.isClass) {
                    classes.add(c);
                }
            }
            classes.sort((a, b) -> Integer.compare(a.displayIndex, b.displayIndex));
            if (classPos - 1 < classes.size()) {
                classId = classes.get(classPos - 1).id;
            }
        }
        int categoryId = selectedCategoryId();
        int versionPos = spinnerVersion.getSelectedItemPosition();
        String version = versionPos > 0 && versionPos - 1 < gameVersions.size()
                ? gameVersions.get(versionPos - 1) : "";

        loadingProgress.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);

        int index = (currentPage - 1) * PAGE_SIZE;

        client.searchContent(query, categoryId > 0 ? categoryId : classId, version, index,
                PAGE_SIZE, currentSort.field, "desc",
                new CurseForgeClient.CurseForgeCallback<ContentSearchResponse>() {
                    @Override
                    public void onSuccess(ContentSearchResponse result) {
                        handler.post(() -> {
                            loadingProgress.setVisibility(View.GONE);
                            recyclerView.setVisibility(View.VISIBLE);
                            if (result != null && result.data != null) {
                                if (result.pagination != null && result.pagination.totalCount > 0) {
                                    totalPages = (int) Math.ceil((double) result.pagination.totalCount / PAGE_SIZE);
                                } else if (result.data.size() < PAGE_SIZE) {
                                    totalPages = currentPage;
                                } else {
                                    totalPages = currentPage + 1;
                                }
                                adapter.setContents(result.data, currentPage, totalPages);
                                if (result.data.isEmpty()) {
                                    Toast.makeText(CurseForgeActivity.this,
                                            R.string.no_mods_found, Toast.LENGTH_SHORT).show();
                                }
                                recyclerView.scrollToPosition(0);
                            } else {
                                adapter.setContents(Collections.emptyList(), 1, 1);
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable t) {
                        handler.post(() -> {
                            loadingProgress.setVisibility(View.GONE);
                            recyclerView.setVisibility(View.VISIBLE);
                            Toast.makeText(CurseForgeActivity.this,
                                    "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    private void onContentClick(Content content) {
        Intent intent = new Intent(this, ContentDetailsActivity.class);
        intent.putExtra(ContentDetailsActivity.EXTRA_CONTENT, content);
        startActivity(intent);
    }
}
