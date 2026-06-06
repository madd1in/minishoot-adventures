package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.game.GameScreen
import com.example.game.GameViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val gameViewModel: GameViewModel = viewModel()

      MyApplicationTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          // Secure auto-cleanup loop of game threads when UI context is left
          DisposableEffect(Unit) {
            onDispose {
              gameViewModel.stopTickEngine()
            }
          }

          BoxWithScaffoldPadding(modifier = Modifier.padding(innerPadding)) {
            GameScreen(viewModel = gameViewModel)
          }
        }
      }
    }
  }
}

@Composable
fun BoxWithScaffoldPadding(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  content()
}

