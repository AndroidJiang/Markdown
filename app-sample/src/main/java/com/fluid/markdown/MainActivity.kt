package com.fluid.markdown

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.fluid.afm.AFMInitializer
import com.fluid.afm.R
import com.fluid.afm.markdown.ElementClickEventCallback
import com.fluid.afm.markdown.html.SpanTextClickableSpan.ClickableTextType
import com.fluid.afm.markdown.model.EventModel
import com.fluid.afm.markdown.widget.PrinterMarkDownTextView
import com.fluid.afm.styles.MarkdownStyles
import com.fluid.afm.styles.TitleStyle
import com.fluid.markdown.chat.ChatActivity
import com.fluid.markdown.demos.ListActivity
import com.fluid.markdown.demos.PrinterActivity


class MainActivity : AppCompatActivity() {
    companion object {
        @JvmStatic
        var initialed = false
    }

    private lateinit var markdownTextView: PrinterMarkDownTextView
    private lateinit var tagContainer: LinearLayout
    private lateinit var scrollView: NestedScrollView
    private val tagViews = mutableListOf<TextView>()
    private var selectedIndex = 0

    private data class TagItem(val labelResId: Int, val contentResId: Int)

    private val tagItems by lazy {
        listOf(
            TagItem(R.string.tag_all, R.string.sample),
            TagItem(R.string.tag_heading, R.string.sample_heading),
            TagItem(R.string.tag_text_style, R.string.sample_text_style),
            TagItem(R.string.tag_list, R.string.sample_list),
            TagItem(R.string.tag_blockquote, R.string.sample_blockquote),
            TagItem(R.string.tag_table, R.string.sample_table),
            TagItem(R.string.tag_code, R.string.sample_code),
            TagItem(R.string.tag_link_image, R.string.sample_link_image),
            TagItem(R.string.tag_footnote, R.string.sample_footnote),
            TagItem(R.string.tag_html, R.string.sample_html),
            TagItem(R.string.tag_hr, R.string.sample_hr),
            TagItem(R.string.tag_weather, R.string.sample_weather),
            TagItem(R.string.tag_printer, 0),
            TagItem(R.string.tag_ai_chat, -1)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_main)

        if (!initialed) {
            initialed = true
            AFMInitializer.init(this, null, MyImageHandler(), null)
        }

        markdownTextView = findViewById(R.id.textView)
        tagContainer = findViewById(R.id.tagContainer)
        scrollView = findViewById(R.id.scrollView)

        initMarkdownView()
        buildTagButtons()
        selectTag(0)

        scrollView.setOnScrollChangeListener { _: NestedScrollView, _: Int, _: Int, _: Int, _: Int ->
            markdownTextView.handleExposureSpm()
        }
    }

    private fun initMarkdownView() {
        val styles = MarkdownStyles.getDefaultStyles()
        styles.linkStyle().icon("local://mipmap/link")
        styles.setTitleStyle(0, TitleStyle.create(1.5f).icon("local://mipmap/title"))
        markdownTextView.init(styles, object : ElementClickEventCallback {
            override fun onLinkClicked(params: Map<String?, Any?>?): Boolean {
                val url = params?.get(ElementClickEventCallback.PARAM_KEY_LINK) as String?
                if (url == "open://printer") {
                    startActivity(Intent(this@MainActivity, PrinterActivity::class.java))
                    return true
                } else if (url == "open://list_printer") {
                    startActivity(Intent(this@MainActivity, ListActivity::class.java))
                    return true
                }
                Toast.makeText(this@MainActivity, "link click$url", Toast.LENGTH_SHORT).show()
                return false
            }

            override fun onFootnoteClicked(index: String) {
                Toast.makeText(this@MainActivity, "footnote click$index", Toast.LENGTH_SHORT).show()
            }

            override fun onImageClicked(url: String?, description: String?) {
                Toast.makeText(this@MainActivity, "onImageClicked click-$description", Toast.LENGTH_SHORT).show()
            }

            override fun onTextClickableSpanClicked(
                widget: View?,
                link: String?,
                entityID: String?,
                type: ClickableTextType?
            ): Boolean {
                Toast.makeText(this@MainActivity, "clickable click link$link type$type entityID$entityID", Toast.LENGTH_SHORT).show()
                return false
            }

            override fun exposureSpmBehavior(models: MutableList<EventModel>?) {
                Log.d("exposureSpmBehavior", models.toString())
            }
        })
    }

    private fun buildTagButtons() {
        val horizontalPadding = dpToPx(14f).toInt()
        val verticalPadding = dpToPx(6f).toInt()
        val margin = dpToPx(4f).toInt()

        tagItems.forEachIndexed { index, item ->
            val tv = TextView(this).apply {
                text = getString(item.labelResId)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(margin, 0, margin, 0)
                layoutParams = lp
                setOnClickListener { selectTag(index) }
            }
            tagContainer.addView(tv)
            tagViews.add(tv)
        }
    }

    private fun selectTag(index: Int) {
        selectedIndex = index
        tagViews.forEachIndexed { i, tv ->
            if (i == index) {
                tv.setBackgroundResource(R.drawable.tag_bg_selected)
                tv.setTextColor(0xFFFFFFFF.toInt())
            } else {
                tv.setBackgroundResource(R.drawable.tag_bg_normal)
                tv.setTextColor(0xFF666666.toInt())
            }
        }

        val item = tagItems[index]
        if (item.contentResId == -1) {
            startActivity(Intent(this, ChatActivity::class.java))
            selectTag(0)
            return
        }
        if (item.contentResId == 0) {
            startActivity(Intent(this, PrinterActivity::class.java))
            selectTag(0)
            return
        }

        scrollView.scrollTo(0, 0)

        val markdown = if (index == 0) {
            resources.getString(R.string.toPrinter) +
                    resources.getString(R.string.toList) +
                    resources.getString(R.string.sample)
        } else {
            resources.getString(item.contentResId)
        }

        if (markdownTextView.isStarted) {
            markdownTextView.stopPrinting("")
        }

        val isWeatherTab = item.labelResId == R.string.tag_weather
        if (isWeatherTab) {
            markdownTextView.setSizeChangedListener { _, _ ->
                scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
            }
            markdownTextView.setMarkdownText("")
            markdownTextView.startPrinting(markdown)
        } else {
            markdownTextView.setSizeChangedListener(null)
            markdownTextView.setMarkdownText(markdown)
        }
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }
}
