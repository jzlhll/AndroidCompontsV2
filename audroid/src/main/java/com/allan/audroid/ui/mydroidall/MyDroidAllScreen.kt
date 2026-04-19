package com.allan.audroid.ui.mydroidall

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.allan.audroid.R
import com.allan.audroid.theme.LogicButtonTextStyle
import com.allan.audroid.theme.LogicReceiveColor
import com.allan.audroid.theme.LogicSendColor

@Composable
fun MyDroidAllScreen() {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(21.dp))

        Button(
            onClick = { /* 纯UI无逻辑 */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LogicReceiveColor)
        ) {
            Text(
                text = stringResource(id = R.string.file_receive),
                style = LogicButtonTextStyle
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { /* 纯UI无逻辑 */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LogicSendColor)
        ) {
            Text(
                text = stringResource(id = R.string.file_send),
                style = LogicButtonTextStyle
            )
        }
    }
}
