// The JNI side of com.ka1s.lynx.NativeEngine (android/app/src/main/java/com/ka1s/lynx/NativeEngine.java). A handle is
// the address of a lynx::android::Engine; the Java side makes it with create() and gives it back to destroy() once the
// audio and drawing threads that use it have stopped.

#include <jni.h>

#include <vector>

#include "AndroidEngine.h"
#include "core/Song.h"

namespace
{
lynx::android::Engine* engineOf (jlong handle) noexcept
{
    return reinterpret_cast<lynx::android::Engine*> (handle);
}

// null when it worked, else the message
jstring errorText (JNIEnv* env, const std::string& error)
{
    return error.empty() ? nullptr : env->NewStringUTF (error.c_str());
}
} // namespace

extern "C"
{
JNIEXPORT jlong JNICALL Java_com_ka1s_lynx_NativeEngine_create (JNIEnv*, jclass, jint sampleRate)
{
    return reinterpret_cast<jlong> (new lynx::android::Engine ((double) sampleRate));
}

JNIEXPORT void JNICALL Java_com_ka1s_lynx_NativeEngine_destroy (JNIEnv*, jclass, jlong handle)
{
    delete engineOf (handle);
}

JNIEXPORT jint JNICALL Java_com_ka1s_lynx_NativeEngine_ppq (JNIEnv*, jclass)
{
    return (jint) lynx::ppq;
}

JNIEXPORT jstring JNICALL Java_com_ka1s_lynx_NativeEngine_loadTemplate (JNIEnv* env, jclass, jlong handle, jint index)
{
    return errorText (env, engineOf (handle)->loadTemplate ((int) index));
}

JNIEXPORT jstring JNICALL Java_com_ka1s_lynx_NativeEngine_loadFlp (JNIEnv* env, jclass, jlong handle, jbyteArray bytes)
{
    const auto size = (std::size_t) env->GetArrayLength (bytes);
    std::vector<std::uint8_t> copy (size);
    if (size > 0)
        env->GetByteArrayRegion (bytes, 0, (jsize) size, reinterpret_cast<jbyte*> (copy.data()));
    return errorText (env, engineOf (handle)->loadFlp (copy.data(), copy.size()));
}

JNIEXPORT void JNICALL Java_com_ka1s_lynx_NativeEngine_setPlaying (JNIEnv*, jclass, jlong handle, jboolean on)
{
    engineOf (handle)->setPlaying (on != JNI_FALSE);
}

JNIEXPORT void JNICALL Java_com_ka1s_lynx_NativeEngine_rewind (JNIEnv*, jclass, jlong handle)
{
    engineOf (handle)->rewind();
}

JNIEXPORT jdouble JNICALL Java_com_ka1s_lynx_NativeEngine_positionTicks (JNIEnv*, jclass, jlong handle)
{
    return (jdouble) engineOf (handle)->positionTicks();
}

// `buffer` is a direct buffer of at least frames * 2 floats (the audio thread's, reused for every block)
JNIEXPORT void JNICALL Java_com_ka1s_lynx_NativeEngine_render (JNIEnv* env, jclass, jlong handle, jobject buffer, jint frames)
{
    auto* out = static_cast<float*> (env->GetDirectBufferAddress (buffer));
    if (out != nullptr)
        engineOf (handle)->render (out, (int) frames);
}

JNIEXPORT jstring JNICALL Java_com_ka1s_lynx_NativeEngine_patternName (JNIEnv* env, jclass, jlong handle)
{
    return env->NewStringUTF (engineOf (handle)->view().name.c_str());
}

JNIEXPORT jdouble JNICALL Java_com_ka1s_lynx_NativeEngine_bpm (JNIEnv*, jclass, jlong handle)
{
    return (jdouble) engineOf (handle)->view().bpm;
}

JNIEXPORT jlong JNICALL Java_com_ka1s_lynx_NativeEngine_lengthTicks (JNIEnv*, jclass, jlong handle)
{
    return (jlong) engineOf (handle)->view().lengthTicks;
}

// start, length, key and channel of every note, one after the other
JNIEXPORT jintArray JNICALL Java_com_ka1s_lynx_NativeEngine_notes (JNIEnv* env, jclass, jlong handle)
{
    const auto view = engineOf (handle)->view();
    auto* result = env->NewIntArray ((jsize) view.notes.size());
    if (result != nullptr && ! view.notes.empty())
        env->SetIntArrayRegion (result, 0, (jsize) view.notes.size(), reinterpret_cast<const jint*> (view.notes.data()));
    return result;
}
} // extern "C"
