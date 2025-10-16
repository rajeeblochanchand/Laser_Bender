package com.example.laserbender

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.laserbender.presentation.canvas.LaserCanvasView
import yuku.ambilwarna.AmbilWarnaDialog
import java.io.OutputStream
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var laserCanvas: LaserCanvasView
    private lateinit var btnAddLight: ImageButton
    private lateinit var btnAddMirror: ImageButton
    private lateinit var btnAddFlag: ImageButton
    private lateinit var btnDelete: ImageButton
    private lateinit var btnChangeColor: ImageButton
    private lateinit var btnSave: ImageButton
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnResetView: ImageButton
    private lateinit var btnLockView: ImageButton
    private lateinit var tvZoomLevel: TextView
    private lateinit var buttonContainer: LinearLayout

    private var defaultColor: Int = Color.RED

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Find views
        laserCanvas = findViewById(R.id.laserCanvas)
        buttonContainer = findViewById(R.id.buttonContainer)
        btnAddLight = findViewById(R.id.btnAddLight)
        btnAddMirror = findViewById(R.id.btnAddMirror)
        btnAddFlag = findViewById(R.id.btnAddFlag)
        btnDelete = findViewById(R.id.btnDelete)
        btnChangeColor = findViewById(R.id.btnChangeColor)
        btnSave = findViewById(R.id.btnSave)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnResetView = findViewById(R.id.btnResetView)
        btnLockView = findViewById(R.id.btnLockView)
        tvZoomLevel = findViewById(R.id.tvZoomLevel)

        setupButtons()
        setupCanvasListeners()

        buttonContainer.post {
            val width = btnAddLight.width
            val buttons = listOf(btnAddLight, btnAddMirror, btnAddFlag, btnDelete, btnChangeColor, btnSave)
            for (button in buttons) {
                val params = button.layoutParams
                params.height = width
                button.layoutParams = params
            }
        }
        updateUndoRedoButtons()
        updateZoomLabel(1f)
    }

    private fun setupButtons() {
        btnAddLight.setOnClickListener { laserCanvas.addLight() }
        btnAddMirror.setOnClickListener { laserCanvas.addMirror() }
        btnAddFlag.setOnClickListener { laserCanvas.addFlag() }
        btnDelete.setOnClickListener { laserCanvas.deleteSelected() }
        btnChangeColor.setOnClickListener { openColorPicker() }
        btnUndo.setOnClickListener { laserCanvas.undo() }
        btnRedo.setOnClickListener { laserCanvas.redo() }
        btnResetView.setOnClickListener { laserCanvas.resetView() }
        btnLockView.setOnClickListener { 
            val isLocked = laserCanvas.toggleViewLock()
            if (isLocked) {
                btnLockView.setImageResource(R.drawable.ic_lock)
                btnResetView.isEnabled = false
                btnResetView.alpha = 0.5f
            } else {
                btnLockView.setImageResource(R.drawable.ic_lock_open)
                btnResetView.isEnabled = true
                btnResetView.alpha = 1.0f
            }
        }

        btnSave.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveCanvas()
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    saveCanvas()
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
                }
            }
        }
    }

    private fun setupCanvasListeners() {
        laserCanvas.setOnSelectionChangedListener { hasSelection, isLightSource ->
            btnDelete.visibility = if (hasSelection) View.VISIBLE else View.INVISIBLE
            btnChangeColor.visibility = if (isLightSource) View.VISIBLE else View.INVISIBLE
        }

        laserCanvas.setOnHistoryChangedListener {
            updateUndoRedoButtons()
        }

        laserCanvas.setOnZoomChangedListener {
            updateZoomLabel(it)
        }
    }

    private fun updateUndoRedoButtons() {
        btnUndo.isEnabled = laserCanvas.canUndo()
        btnRedo.isEnabled = laserCanvas.canRedo()
        btnUndo.alpha = if (laserCanvas.canUndo()) 1.0f else 0.5f
        btnRedo.alpha = if (laserCanvas.canRedo()) 1.0f else 0.5f
    }

    private fun updateZoomLabel(scale: Float) {
        tvZoomLevel.text = String.format(Locale.getDefault(), "%.0f%%", scale * 100)
    }

    private fun openColorPicker() {
        val dialog = AmbilWarnaDialog(this, defaultColor, object : AmbilWarnaDialog.OnAmbilWarnaListener {
            override fun onCancel(dialog: AmbilWarnaDialog?) {}

            override fun onOk(dialog: AmbilWarnaDialog?, color: Int) {
                defaultColor = color
                laserCanvas.changeSelectedLightColor(color)
            }
        })
        dialog.show()
    }

    private fun saveCanvas() {
        val bitmap = laserCanvas.getBitmap()
        val fileName = "LaserBender_${System.currentTimeMillis()}.png"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/LaserBender")
            }
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            val outputStream: OutputStream? = resolver.openOutputStream(it)
            outputStream?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                Toast.makeText(this, "Image saved to Gallery", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "Failed to create new MediaStore record", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveCanvas()
            } else {
                Toast.makeText(this, "Permission denied. Cannot save image.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}