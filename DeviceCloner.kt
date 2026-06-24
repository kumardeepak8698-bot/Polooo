package com.cloner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    
    private lateinit var appList: RecyclerView
    private lateinit var adapter: AppListAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        appList = findViewById(R.id.appList)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        
        appList.layoutManager = LinearLayoutManager(this)
        
        checkPermissions()
        loadApps()
    }
    
    private fun checkPermissions() {
        val perms = listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.REQUEST_INSTALL_PACKAGES,
            Manifest.permission.QUERY_ALL_PACKAGES
        )
        
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        }
    }
    
    private fun loadApps() {
        progressBar.visibility = View.VISIBLE
        val apps = PackageManagerHelper.getInstalledApps(this)
        adapter = AppListAdapter(apps) { appInfo ->
            cloneApp(appInfo)
        }
        appList.adapter = adapter
        progressBar.visibility = View.GONE
        statusText.text = "${apps.size} apps loaded"
    }
    
    private fun cloneApp(appInfo: AppInfo) {
        // Show progress
        Toast.makeText(this, "Cloning ${appInfo.name}...", Toast.LENGTH_LONG).show()
        
        // Clone using APKTool + Inject Frida + Randomize
        CloneEngine.clone(this, appInfo) { result ->
            runOnUiThread {
                if (result.success) {
                    Toast.makeText(this, "✅ Clone ready! Installing...", Toast.LENGTH_LONG).show()
                    installApk(result.apkPath)
                } else {
                    Toast.makeText(this, "❌ Failed: ${result.error}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun installApk(apkPath: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                Uri.fromFile(java.io.File(apkPath)),
                "application/vnd.android.package-archive"
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }
}
