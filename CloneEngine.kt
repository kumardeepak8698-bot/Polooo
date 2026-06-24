package com.cloner

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

object CloneEngine {
    
    data class CloneResult(
        val success: Boolean,
        val apkPath: String = "",
        val newPackageName: String = "",
        val error: String = ""
    )
    
    fun clone(context: Context, appInfo: AppInfo, callback: (CloneResult) -> Unit) {
        Thread {
            try {
                val pm = context.packageManager
                val appPath = pm.getApplicationInfo(appInfo.packageName, 0).sourceDir
                val originalApk = File(appPath)
                
                // Create working directory
                val workDir = File(context.filesDir, "clones/${appInfo.packageName.replace(".", "_")}")
                workDir.mkdirs()
                
                // Copy original APK
                val tempApk = File(workDir, "original.apk")
                originalApk.copyTo(tempApk, overwrite = true)
                
                // Generate new package name
                val newPkg = "com.clone.${appInfo.packageName.replace(".", "_")}"
                
                // Decompile with APKTool
                val decompileDir = File(workDir, "decompiled")
                val decompileCmd = "java -jar ${context.filesDir}/apktool.jar d ${tempApk.absolutePath} -o ${decompileDir.absolutePath} -f"
                Runtime.getRuntime().exec(decompileCmd).waitFor()
                
                // Modify AndroidManifest.xml
                val manifestFile = File(decompileDir, "AndroidManifest.xml")
                var manifest = manifestFile.readText()
                manifest = manifest.replace(Regex("""package="[^"]+""""), """package="$newPkg"""")
                
                // Add Frida gadget injection
                val fridaMeta = """
                    <meta-data android:name="frida:scripts" android:value="spoof.js"/>
                    <meta-data android:name="frida:gadget" android:value="true"/>
                """.trimIndent()
                manifest = manifest.replace("<application", "<application\n        $fridaMeta")
                manifestFile.writeText(manifest)
                
                // Create spoof.js
                val assetsDir = File(decompileDir, "assets")
                assetsDir.mkdirs()
                File(assetsDir, "spoof.js").writeText(generateSpoofScript())
                
                // Add Frida gadget .so
                val libDir = File(decompileDir, "lib/arm64-v8a")
                libDir.mkdirs()
                
                // Copy frida gadget from app's native libs
                val gadgetFile = File("/data/data/${context.packageName}/lib/libfrida-gadget.so")
                if (gadgetFile.exists()) {
                    gadgetFile.copyTo(File(libDir, "libfrida-gadget.so"), overwrite = true)
                }
                
                // Recompile
                val unsignedApk = File(workDir, "unsigned.apk")
                val recompileCmd = "java -jar ${context.filesDir}/apktool.jar b ${decompileDir.absolutePath} -o ${unsignedApk.absolutePath}"
                Runtime.getRuntime().exec(recompileCmd).waitFor()
                
                // Sign
                val signedApk = File(workDir, "cloned.apk")
                val signCmd = "java -jar ${context.filesDir}/uber-apk-signer.jar --apk ${unsignedApk.absolutePath} --out ${signedApk.absolutePath}"
                Runtime.getRuntime().exec(signCmd).waitFor()
                
                callback(CloneResult(true, signedApk.absolutePath, newPkg))
                
            } catch (e: Exception) {
                callback(CloneResult(false, error = e.message ?: "Unknown error"))
            }
        }.start()
    }
    
    private fun generateSpoofScript(): String {
        return """
Java.perform(function() {
    function rh(l){return Array.from({length:l},()=>'0123456789abcdef'[Math.floor(Math.random()*16)]).join('')}
    function rm(){return Array.from({length:6},()=>Math.floor(Math.random()*256).toString(16).padStart(2,'0')).join(':')}
    
    var B = Java.use('android.os.Build');
    B.MODEL.value = ['Pixel 9 Pro','Galaxy S25','OnePlus 13','Xiaomi 14','Nothing 3'][Math.floor(Math.random()*5)];
    B.MANUFACTURER.value = ['google','samsung','oneplus','xiaomi','nothing'][Math.floor(Math.random()*5)];
    B.FINGERPRINT.value = 'google/'+rh(8)+'/'+rh(8)+':15/'+rh(6)+'/'+rh(8)+':user/release-keys';
    B.SERIAL.value = rh(8).toUpperCase();
    
    var S = Java.use('android.provider.Settings$Secure');
    S.getString.overload('android.content.ContentResolver','java.lang.String').implementation = function(cr,n) {
        if (n === 'android_id') return rh(16);
        if (n === 'bluetooth_address' || n === 'wlan_mac') return rm();
        return this.getString(cr, n);
    };
    
    var T = Java.use('android.telephony.TelephonyManager');
    T.getDeviceId.overload().implementation = function(){ return '35'+Array(13).fill(0).map(()=>Math.floor(Math.random()*10)).join('') };
    T.getImei.overload().implementation = function(){ return '35'+Array(13).fill(0).map(()=>Math.floor(Math.random()*10)).join('') };
    
    var W = Java.use('android.net.wifi.WifiInfo');
    W.getMacAddress.implementation = function(){ return rm() };
    
    console.log('[Cloner] Device spoofed!');
});
        """.trimIndent()
    }
}
