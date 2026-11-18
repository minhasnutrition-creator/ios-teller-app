package com.minhas.teller

import com.minhas.teller.data.AppDatabase
import com.minhas.teller.database.getIosDatabaseBuilder
import platform.Foundation.*
import platform.UIKit.*

class IOSPlatform : Platform {
    override val name: String = "iOS"
    override val osVersion: String = UIDevice.currentDevice.systemVersion
    override val isIOS: Boolean = true
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

private var databaseInstance: AppDatabase? = null

actual fun getAppDatabase(): AppDatabase {
    return databaseInstance ?: run {
        val instance = getIosDatabaseBuilder().build()
        databaseInstance = instance
        instance
    }
}

actual object PlatformUtils {
    actual fun showToast(message: String) {
        val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (rootViewController != null) {
            val alert = UIAlertController.alertControllerWithTitle(
                title = null,
                message = message,
                preferredStyle = UIAlertControllerStyleAlert
            )
            rootViewController.presentViewController(alert, animated = true) {
                // Auto dismiss after 1.5 seconds
                dispatch_after(
                    dispatch_time(DISPATCH_TIME_NOW, (1.5 * NSEC_PER_SEC.toDouble()).toLong()),
                    dispatch_get_main_queue()
                ) {
                    alert.dismissViewControllerAnimated(true, completion = null)
                }
            }
        }
    }

    actual fun shareText(text: String, title: String) {
        val activityViewController = UIActivityViewController(
            activityItems = listOf(text),
            applicationActivities = null
        )
        val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootViewController?.presentViewController(activityViewController, animated = true, completion = null)
    }

    actual fun openUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl != null) {
            UIApplication.sharedApplication.openURL(nsUrl)
        }
    }

    actual fun getAppDataDirectory(): String {
        val fileManager = NSFileManager.defaultManager
        val urls = fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        val documentUrl = urls.firstOrNull() as? NSURL
        return documentUrl?.path ?: ""
    }

    actual fun getAppVersion(): String {
        val infoDict = NSBundle.mainBundle.infoDictionary
        val version = infoDict?.get("CFBundleShortVersionString") as? String
        return version ?: "6.7.43"
    }
}
