package io.github.leodias.httpie

import com.intellij.openapi.util.IconLoader
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object HttpieLanguage : Language("HTTPieRequests")

object HttpieFileType : LanguageFileType(HttpieLanguage) {
    override fun getName(): String = "HTTPie"
    override fun getDescription(): String = "HTTPie requests"
    override fun getDefaultExtension(): String = "httpie"
    override fun getIcon(): Icon = IconLoader.getIcon("/icons/httpieFile.svg", HttpieFileType::class.java)
}
