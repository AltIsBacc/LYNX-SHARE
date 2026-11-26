#include <jni.h>

#include <string>
#include <vector>

#include "AndroidEngine.h"
#include "core/Song.h"

namespace
{
lynx::android::Engine* engineOf (jlong handle) noexcept
{
    return reinterpret_cast<lynx::android::Engine*> (handle);
}

jstring errorText (JNIEnv* env, const std::string& error)
{
    return error.empty() ? nullptr : env->NewStringUTF (error.c_str());
}
}

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

JNIEXPORT jint JNICALL Java_com_ka1s_lynx_NativeEngine_ticksPerStep (JNIEnv*, jclass)
{
    return (jint) lynx::ticksPerStep;
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

JNIEXPORT jboolean JNICALL Java_com_ka1s_lynx_NativeEngine_toggleStep (JNIEnv*, jclass, jlong handle, jint channel, jint step)
{
    return engineOf (handle)->toggleStep ((int) channel, (int) step) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_ka1s_lynx_NativeEngine_toggleNote (JNIEnv*, jclass, jlong handle, jint channel, jint key, jint step)
{
    return engineOf (handle)->toggleNote ((int) channel, (int) key, (int) step) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_ka1s_lynx_NativeEngine_setTempo (JNIEnv*, jclass, jlong handle, jdouble bpm)
{
    engineOf (handle)->setTempo ((double) bpm);
}

JNIEXPORT void JNICALL Java_com_ka1s_lynx_NativeEngine_setChannelMuted (JNIEnv*, jclass, jlong handle, jint channel, jboolean muted)
{
    engineOf (handle)->setChannelMuted ((int) channel, muted != JNI_FALSE);
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

JNIEXPORT jintArray JNICALL Java_com_ka1s_lynx_NativeEngine_notes (JNIEnv* env, jclass, jlong handle)
{
    const auto view = engineOf (handle)->view();
    auto* result = env->NewIntArray ((jsize) view.notes.size());
    if (result != nullptr && ! view.notes.empty())
        env->SetIntArrayRegion (result, 0, (jsize) view.notes.size(), reinterpret_cast<const jint*> (view.notes.data()));
    return result;
}

JNIEXPORT jobjectArray JNICALL Java_com_ka1s_lynx_NativeEngine_channelNames (JNIEnv* env, jclass, jlong handle)
{
    const auto channels = engineOf (handle)->channels();
    auto* stringClass = env->FindClass ("java/lang/String");
    auto* result = env->NewObjectArray ((jsize) channels.size(), stringClass, nullptr);
    for (std::size_t i = 0; i < channels.size(); ++i)
    {
        auto* name = env->NewStringUTF (channels[i].name.c_str());
        env->SetObjectArrayElement (result, (jsize) i, name);
        env->DeleteLocalRef (name);
    }
    return result;
}

JNIEXPORT jintArray JNICALL Java_com_ka1s_lynx_NativeEngine_channelColours (JNIEnv* env, jclass, jlong handle)
{
    const auto channels = engineOf (handle)->channels();
    std::vector<jint> colours;
    colours.reserve (channels.size());
    for (const auto& channel : channels)
        colours.push_back ((jint) channel.colour);

    auto* result = env->NewIntArray ((jsize) colours.size());
    if (result != nullptr && ! colours.empty())
        env->SetIntArrayRegion (result, 0, (jsize) colours.size(), colours.data());
    return result;
}

JNIEXPORT jbooleanArray JNICALL Java_com_ka1s_lynx_NativeEngine_channelMuted (JNIEnv* env, jclass, jlong handle)
{
    const auto channels = engineOf (handle)->channels();
    std::vector<jboolean> flags;
    flags.reserve (channels.size());
    for (const auto& channel : channels)
        flags.push_back (channel.muted ? JNI_TRUE : JNI_FALSE);

    auto* result = env->NewBooleanArray ((jsize) flags.size());
    if (result != nullptr && ! flags.empty())
        env->SetBooleanArrayRegion (result, 0, (jsize) flags.size(), flags.data());
    return result;
}
}
