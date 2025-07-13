package edu.unicauca.app.agrochat

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity // Cambiado de AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold // Puedes usarlo si necesitas estructura como TopAppBar, FAB, etc.
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember // Necesario para remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import edu.unicauca.app.agrochat.mindspore.MindSporeHelper
import edu.unicauca.app.agrochat.ui.theme.AgroChatTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() { // Cambiado de AppCompatActivity

    private lateinit var mindSporeHelper: MindSporeHelper
    // Para mostrar el estado en la UI de Compose
    private var mindSporeStatus by mutableStateOf("Inicializando MindSpore...")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Opcional, para UI de borde a borde

        mindSporeHelper = MindSporeHelper(applicationContext)

        lifecycleScope.launch {
            val success = mindSporeHelper.initialize()
            if (success) {
                mindSporeStatus = "MindSpore Helper inicializado exitosamente."
                Log.i("MainActivity", mindSporeStatus)
            } else {
                mindSporeStatus = "Fallo al inicializar MindSpore Helper."
                Log.e("MainActivity", mindSporeStatus)
            }
        }

        setContent {
            AgroChatTheme { // Usa tu tema de Compose definido en ui.theme
                // Scaffold es opcional si solo quieres mostrar texto, pero es una buena estructura base.
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        statusMessage = mindSporeStatus,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mindSporeHelper.release()
        Log.d("MainActivity", "MindSporeHelper liberado.")
    }
}

// Un Composable simple para mostrar el estado de MindSpore
@Composable
fun MainScreen(statusMessage: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center // Centra el texto
    ) {
        Text(text = statusMessage)
    }
}

// Preview para MainScreen
@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    AgroChatTheme {
        MainScreen("Estado de prueba para la vista previa")
    }
}

// Tu @Composable Greeting y GreetingPreview que ya tenías (puedes mantenerlos o eliminarlos si no los usas)
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AgroChatTheme {
        Greeting("Android")
    }
}
