package com.timetochilltoo.fileviewer.feature.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfAnnotationSpikeTest {

    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        cacheDir = context.cacheDir
    }

    private fun loadFixture(): PDDocument {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val file = File(cacheDir, "fixture_hello.pdf")
        testContext.assets.open("fixture_hello.pdf").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return PDDocument.load(file)
    }

    @Test
    fun highlightAnnotationSurvivesWriteAndReload() {
        loadFixture().use { doc ->
            val page = doc.getPage(0)
            val highlight = PDAnnotationTextMarkup(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT)
            highlight.rectangle = PDRectangle(72f, 712f, 220f, 26f)
            highlight.quadPoints = floatArrayOf(72f, 738f, 292f, 738f, 72f, 712f, 292f, 712f)
            highlight.color = PDColor(floatArrayOf(1f, 1f, 0f), PDDeviceRGB.INSTANCE)
            highlight.constantOpacity = 0.55f
            page.annotations.add(highlight)

            val out = File(cacheDir, "highlighted.pdf")
            doc.save(out)

            PDDocument.load(out).use { reloaded ->
                val annotations = reloaded.getPage(0).annotations
                assertEquals(1, annotations.size)
                val restored = annotations[0]
                assertTrue(restored is PDAnnotationTextMarkup)
                assertEquals(
                    PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT,
                    (restored as PDAnnotationTextMarkup).subtype,
                )
                assertEquals(8, restored.quadPoints?.size)
            }
        }
    }

    @Test
    fun freeTextAnnotationSurvivesWriteAndReload() {
        loadFixture().use { doc ->
            val page = doc.getPage(0)
            // pdfbox-android does not ship PDAnnotationFreeText; build it at COS level.
            val dict = COSDictionary()
            dict.setItem(COSName.TYPE, COSName.ANNOT)
            dict.setName(COSName.SUBTYPE, "FreeText")
            val rect = COSArray()
            listOf(72f, 620f, 292f, 660f).forEach { rect.add(COSFloat(it)) }
            dict.setItem(COSName.RECT, rect)
            dict.setString(COSName.CONTENTS, "Spike free text")
            dict.setString(COSName.DA, "/Helv 12 Tf 0 g")
            page.annotations.add(PDAnnotationMarkup(dict))

            val out = File(cacheDir, "freetext.pdf")
            doc.save(out)

            PDDocument.load(out).use { reloaded ->
                val annotations = reloaded.getPage(0).annotations
                assertEquals(1, annotations.size)
                val restored = annotations[0]
                assertEquals("FreeText", restored.cosObject.getNameAsString(COSName.SUBTYPE))
                assertEquals("Spike free text", restored.cosObject.getString(COSName.CONTENTS))
            }
        }
    }

    @Test
    fun formFieldValueSurvivesFillAndReload() {
        val formFile = File(cacheDir, "form.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.LETTER)
            doc.addPage(page)
            val acroForm = PDAcroForm(doc)
            val resources = PDResources()
            resources.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA)
            acroForm.defaultResources = resources
            doc.documentCatalog.acroForm = acroForm

            val textField = PDTextField(acroForm)
            textField.partialName = "name_field"
            acroForm.fields.add(textField)
            val widget = textField.widgets[0]
            widget.rectangle = PDRectangle(72f, 600f, 220f, 20f)
            widget.page = page
            page.annotations.add(widget)
            doc.save(formFile)
        }

        val filledFile = File(cacheDir, "form_filled.pdf")
        PDDocument.load(formFile).use { doc ->
            val field = doc.documentCatalog.acroForm.getField("name_field") as PDTextField
            field.value = "Patrick"
            doc.save(filledFile)
        }

        PDDocument.load(filledFile).use { doc ->
            val field = doc.documentCatalog.acroForm.getField("name_field")
            assertEquals("Patrick", field.valueAsString)
        }
    }
}
