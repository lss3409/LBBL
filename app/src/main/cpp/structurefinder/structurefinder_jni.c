/**
 * JNI 桥：SeedFinder（cubiomes + Bfinders）Bedrock 结构查找。
 * v0.4.57：NDK 内嵌，完全离线按种子计算结构生成位置。
 * 算法来源：cubiomes（MIT）+ chunkbase Bedrock 结构过滤器重实现
 * （Bfinders.c/h）+ SeedFinder 包装层（zlib）。
 */
#include <jni.h>
#include <stdint.h>

/* seedfinder_wrapper.c 导出（C 符号，无 mangling） */
const char *seedfinder_status(void);
char *seedfinder_scan(uint64_t seed, double playerX, double playerZ,
                      int radius, int maxResults,
                      const int *types, int numTypes);
void seedfinder_free_result(char *result);

JNIEXPORT jstring JNICALL
Java_org_levimc_launcher_core_content_worldmap_SeedStructureFinder_nativeScan(
        JNIEnv *env, jclass clazz, jlong seed, jdouble x, jdouble z,
        jint radius, jint max, jintArray types)
{
    if (types == NULL) {
        return NULL;
    }
    jsize n = (*env)->GetArrayLength(env, types);
    if (n <= 0) {
        return NULL;
    }
    jint *arr = (*env)->GetIntArrayElements(env, types, NULL);
    if (arr == NULL) {
        return NULL;
    }
    char *json = seedfinder_scan((uint64_t) seed, (double) x, (double) z,
                                 (int) radius, (int) max, arr, (int) n);
    (*env)->ReleaseIntArrayElements(env, types, arr, JNI_ABORT);
    if (json == NULL) {
        return NULL;
    }
    jstring result = (*env)->NewStringUTF(env, json);
    seedfinder_free_result(json);
    return result;
}
