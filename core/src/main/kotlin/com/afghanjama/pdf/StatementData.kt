package com.afghanjama.pdf

/** یک سطرِ حسابِ مشتری. */
data class StatementRow(
    val date: String,
    val title: String,
    /** بدهکار — چیزی که مشتری برداشته. */
    val debit: Long = 0L,
    /** بستانکار — چیزی که پرداخته. */
    val credit: Long = 0L
)

data class StatementData(
    val customerName: String,
    val customerPhone: String = "",
    val at: Long = System.currentTimeMillis(),
    val rows: List<StatementRow>
) {
    val totalDebit: Long get() = rows.sumOf { it.debit }
    val totalCredit: Long get() = rows.sumOf { it.credit }

    /** مثبت یعنی مشتری بدهکار است، منفی یعنی نزدِ ما پول دارد. */
    val balance: Long get() = totalDebit - totalCredit
}

fun statementColumns(paper: Paper): InvoiceColumns = when {
    paper.narrow -> InvoiceColumns(
        listOf("شرح", "مبلغ", "مانده"),
        listOf(2.4f, 1.3f, 1.3f)
    )
    else -> InvoiceColumns(
        listOf("تاریخ", "شرح", "بدهکار", "بستانکار", "مانده"),
        listOf(1.2f, 2.6f, 1.2f, 1.2f, 1.3f)
    )
}
