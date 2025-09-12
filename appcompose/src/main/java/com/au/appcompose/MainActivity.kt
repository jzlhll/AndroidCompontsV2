package com.au.appcompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.au.appcompose.ui.theme.AndroidCompontsTheme

class MainActivity : ComponentActivity() {
    private var clicks = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidCompontsTheme {
                Scaffold( modifier = Modifier.fillMaxSize() ) { innerPadding ->
                    MainUi(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainUi(name: String, modifier: Modifier = Modifier) {
    Column(
        modifier = Modifier.background(
            color = Color(android.graphics.Color.GREEN),
            shape = RoundedCornerShape(Dp(4f)) // 需要导入androidx.compose.foundation.shape包
        ),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Hello1 $name!",
            modifier = modifier
        )
        Text("Hello2 $name!")
        ClickCounter(cli)
    }
}

@Composable
fun ClickCounter(clicks: Int, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text("I've been clicked $clicks times")
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MainUi("Changed")
}