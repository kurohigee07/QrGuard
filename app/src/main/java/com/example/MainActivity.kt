package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    // ValueCallback to handle HTML file choosing activities for gallery QR images
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    // Register active launcher to open native files picker
    private val galleryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val dataUri = result.data?.data
            val dataClip = result.data?.clipData
            
            val uris = mutableListOf<Uri>()
            if (dataUri != null) {
                uris.add(dataUri)
            } else if (dataClip != null) {
                for (i in 0 until dataClip.itemCount) {
                    uris.add(dataClip.getItemAt(i).uri)
                }
            }
            
            if (uris.isNotEmpty()) {
                fileUploadCallback?.onReceiveValue(uris.toTypedArray())
            } else {
                fileUploadCallback?.onReceiveValue(null)
            }
        } else {
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0512) // Matches Dark Luxury Theme from assets
                ) {
                    ScannerApplicationContainer(
                        onOpenFilePicker = { callback, intent ->
                            fileUploadCallback = callback
                            galleryPickerLauncher.launch(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ScannerApplicationContainer(
    onOpenFilePicker: (ValueCallback<Array<Uri>>, Intent) -> Unit
) {
    val context = LocalContext.current
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        cameraPermissionGranted = isGranted
    }

    // Auto prompt permissions request on launch to ensure frictionless UX
    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (cameraPermissionGranted) {
        // Render Secure WebView Engine
        EmbeddedWebContainer(onOpenFilePicker = onOpenFilePicker)
    } else {
        // Immersive Dark Luxury style fallbacks if permission request is rejected
        PermissionRequestFallbackScreen(
            onRequestPermission = {
                launcher.launch(Manifest.permission.CAMERA)
            }
        )
    }
}

@Composable
fun EmbeddedWebContainer(
    onOpenFilePicker: (ValueCallback<Array<Uri>>, Intent) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0512))
            .testTag("embedded_webview_container")
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    // Critical Secure Config rules for WebView
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    
                    // Prevent external system browsers from launching
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            // Let the sandboxed iframe render locally inside web assets
                            return false
                        }
                    }

                    // Native Web Chrome Client Bridge mapping camera hardware
                    webChromeClient = object : WebChromeClient() {
                        
                        // Bridge 1: Intercept HTML MediaDevices getUserMedia calls and grant
                        override fun onPermissionRequest(request: PermissionRequest) {
                            val requestedResources = request.resources
                            for (resource in requestedResources) {
                                if (resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                                    request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                                    return
                                }
                            }
                            request.grant(requestedResources)
                        }

                        // Bridge 2: Wire native document chooser picker
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            if (filePathCallback == null) return false
                            
                            val customIntent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "image/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                            
                            onOpenFilePicker(filePathCallback, customIntent)
                            return true
                        }
                    }

                    // Load index.html locally with fast local file protocol
                    loadUrl("file:///android_asset/www/index.html")
                }
            }
        )
    }
}

@Composable
fun PermissionRequestFallbackScreen(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0512))
            .padding(24.dp)
            .testTag("permission_fallback_screen"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Luxury Icon Shield
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.4f), Color(0x00000000))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Shield Security Logo",
                tint = Color(0xFF8B5CF6),
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "QR GUARD SECURE",
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Black,
            fontSize = 24.sp,
            color = Color.White,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )

        Text(
            text = "CYBERSECURITY SYSTEM",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFFA78BFA),
            letterSpacing = 4.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Aplikasi memerlukan otorisasi akses kamera untuk melakukan pemindaian barcode / QR keamanan secara real-time pada HP Poco Anda.",
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF8B5CF6),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(54.dp)
                .testTag("request_camera_permission_button")
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Camera Icon"
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Izinkan Hak Akses Kamera",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}
