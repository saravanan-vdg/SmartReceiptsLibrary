package co.smartreceipts.android.identity.organization

import co.smartreceipts.android.model.Column
import co.smartreceipts.android.model.Receipt
import co.smartreceipts.android.model.factory.CategoryBuilderFactory
import co.smartreceipts.android.model.factory.PaymentMethodBuilderFactory
import co.smartreceipts.android.model.impl.columns.receipts.ReceiptCommentColumn
import co.smartreceipts.android.model.impl.columns.receipts.ReceiptNameColumn
import co.smartreceipts.android.persistence.database.controllers.impl.CSVTableController
import co.smartreceipts.android.persistence.database.controllers.impl.CategoriesTableController
import co.smartreceipts.android.persistence.database.controllers.impl.PDFTableController
import co.smartreceipts.android.persistence.database.controllers.impl.PaymentMethodsTableController
import co.smartreceipts.android.sync.model.impl.DefaultSyncState
import com.nhaarman.mockito_kotlin.*
import io.reactivex.Single
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.*

@RunWith(RobolectricTestRunner::class)
class AppSettingsSynchronizerTest {

    // Class under test
    private lateinit var appSettingsSynchronizer: AppSettingsSynchronizer


    private val categoriesTableController = mock<CategoriesTableController>()
    private val paymentMethodsTableController = mock<PaymentMethodsTableController>()
    private val csvTableController = mock<CSVTableController>()
    private val pdfTableController = mock<PDFTableController>()
    private val preferencesSynchronizer = mock<AppPreferencesSynchronizer>()

    private val uuid1 = UUID.randomUUID()
    private val uuid2 = UUID.randomUUID()
    private val category1 = CategoryBuilderFactory().setUuid(uuid1).setCode("CODE_1").build()
    private val category2 = CategoryBuilderFactory().setUuid(uuid2).setCode("CODE_2").build()
    private val paymentMethod1 = PaymentMethodBuilderFactory().setUuid(uuid1).setMethod("METHOD_1").build()
    private val paymentMethod2 = PaymentMethodBuilderFactory().setUuid(uuid2).setMethod("METHOD_2").build()
    private val column1 = ReceiptNameColumn(0, DefaultSyncState(), 0, uuid1)
    private val column2 = ReceiptCommentColumn(5, DefaultSyncState(), 0, uuid2)
    private val column3 = ReceiptNameColumn(0, DefaultSyncState(), 0, UUID.randomUUID())

    @Before
    fun setUp() {
        appSettingsSynchronizer =
            AppSettingsSynchronizer(
                categoriesTableController,
                paymentMethodsTableController,
                csvTableController,
                pdfTableController,
                preferencesSynchronizer
            )

        whenever(categoriesTableController.get()).thenReturn(Single.just(arrayListOf(category1, category2)))
        whenever(paymentMethodsTableController.get()).thenReturn(Single.just(arrayListOf(paymentMethod1, paymentMethod2)))
        whenever(csvTableController.get()).thenReturn(Single.just(arrayListOf(column1, column2) as List<Column<Receipt>>?))
        whenever(pdfTableController.get()).thenReturn(Single.just(arrayListOf(column1, column2) as List<Column<Receipt>>?))
    }

    @Test
    fun checkCategoriesWhenSameTest() {
        // Note: while checking categories, we need to check just uuid+name+code (ignore id because it's local)
        appSettingsSynchronizer.checkCategoriesMatch(arrayListOf(category2, CategoryBuilderFactory(category1).setId(152).build()))
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertResult(true)
    }

    @Test
    fun checkCategoriesWhenNotSameTest() {
        appSettingsSynchronizer.checkCategoriesMatch(
            arrayListOf(category2, CategoryBuilderFactory(category1).setUuid(UUID.randomUUID()).build())
        )
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertResult(false)
    }

    @Test
    fun checkPaymentMethodsWhenSameTest() {
        // Note: while checking payment methods, we need to check just uuid+method (ignore id because it's local)
        appSettingsSynchronizer.checkPaymentMethodsMatch(
            arrayListOf(
                paymentMethod2,
                PaymentMethodBuilderFactory(paymentMethod1).setId(152).build()
            )
        )
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertResult(true)
    }

    @Test
    fun checkPaymentMethodsWhenNotSameTest() {
        appSettingsSynchronizer.checkPaymentMethodsMatch(
            arrayListOf(paymentMethod2, PaymentMethodBuilderFactory(paymentMethod1).setUuid(UUID.randomUUID()).build())
        )
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertResult(false)
    }

    @Test
    fun checkColumnsWhenSameTest() {
        // Note: while checking columns, we need to check just uuid+type
        appSettingsSynchronizer.checkCsvColumnsMatch(arrayListOf(column2, column1)).test()
            .assertNoErrors()
            .assertComplete()
            .assertResult(true)
    }

    @Test
    fun checkColumnsWhenSameButDifferentSizeTest() {
        appSettingsSynchronizer.checkCsvColumnsMatch(arrayListOf(column2)).test()
            .assertNoErrors()
            .assertComplete()
            .assertResult(true)
    }

    @Test
    fun checkColumnsWhenNotSameTest() {
        appSettingsSynchronizer.checkPdfColumnsMatch(arrayListOf(column2, column3)).test()
            .assertNoErrors()
            .assertComplete()
            .assertResult(false)
    }

    @Test
    fun applyCategoriesWhenSame() {
        appSettingsSynchronizer.applyCategories(arrayListOf(category2, category1)).test()
            .assertNoErrors()
            .assertComplete()

        verify(categoriesTableController, never()).update(any(), any(), any())
        verify(categoriesTableController, never()).insert(any(), any())
    }

    @Test
    fun applyCategoriesWhenChanged() {
        val category2Changed = CategoryBuilderFactory(category2).setName("another name").build()
        appSettingsSynchronizer.applyCategories(arrayListOf(category1, category2Changed)).test()
            .assertNoErrors()
            .assertComplete()

        verify(categoriesTableController, times(1)).update(eq(category2), eq(category2Changed), any())
        verify(categoriesTableController, never()).insert(any(), any())
    }

    @Test
    fun applyCategoriesWhenNotFound() {
        val category3 = CategoryBuilderFactory().build()
        appSettingsSynchronizer.applyCategories(arrayListOf(category1, category3)).test()
            .assertNoErrors()
            .assertComplete()

        verify(categoriesTableController, never()).update(any(), any(), any())
        verify(categoriesTableController, times(1)).insert(eq(category3), any())
    }
}