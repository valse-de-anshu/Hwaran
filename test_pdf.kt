import android.graphics.pdf.PdfRenderer
fun main() {
    val methods = PdfRenderer.Page::class.java.methods
    for (m in methods) {
        println(m.name)
    }
}
