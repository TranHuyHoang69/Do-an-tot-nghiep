package com.example.moneymatev2.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.GroupedTransaction

@Composable
fun TransactionItem(
    item: GroupedTransaction,
    onClick: (GroupedTransaction) -> Unit
){
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick(item) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column{
                Text(item.category.name)
                Text(
                    text = "${item.transactionCount} ${stringResource(StringRes.transaction)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val sign = if(item.type == TransactionType.EXPENSE) "-" else "+"
            Text("$sign${item.totalAmount}")
        }
    }
}