package org.example.org.example.service.formatter

import java.text.NumberFormat
import java.util.*
import org.javamoney.moneta.Money

fun Money.format(): String = NumberFormat.getCurrencyInstance(Locale.US).format(this.number)
