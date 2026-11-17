package com.envimesh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.envimesh.app.ui.navigation.EnviMeshApp
import com.envimesh.app.ui.theme.EnviMeshTheme
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // OpenStreetMap tile servers require an identifying user agent.
        Configuration.getInstance().userAgentValue = packageName
        setContent {
            EnviMeshTheme {
                EnviMeshApp()
            }
        }
    }
}
