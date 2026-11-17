package com.minhas.teller.data.entities

data class LedgerWithBalance(
    val ledger: Ledger,
    val balance: Long // paise
)
