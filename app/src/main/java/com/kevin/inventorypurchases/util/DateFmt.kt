package com.kevin.inventorypurchases.util

object DateFmt {
    fun mmDdYyyy(epoch: Long): String {
        val fmt = java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US)
        return fmt.format(java.util.Date(epoch))
    }
}