# Endless Drive – keep game engine entry points if needed later.
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
