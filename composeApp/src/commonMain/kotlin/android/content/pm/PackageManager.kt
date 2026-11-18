package android.content.pm

object PackageManager {
    const val PERMISSION_GRANTED = 0
    const val PERMISSION_DENIED = -1

    class InstallSourceInfo(val installingPackageName: String? = null)

    fun getInstallSourceInfo(packageName: String): InstallSourceInfo = InstallSourceInfo(null)

    @Suppress("DEPRECATION")
    fun getInstallerPackageName(packageName: String): String? = null
}
