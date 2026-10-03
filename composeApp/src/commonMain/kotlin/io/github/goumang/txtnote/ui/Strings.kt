package io.github.goumang.txtnote.ui

import io.github.goumang.txtnote.model.Category
import io.github.goumang.txtnote.model.Language
import io.github.goumang.txtnote.model.ThemeColor

class Strings(val language: Language) {
    fun text(zh: String, en: String, ja: String = en): String = when (language) { Language.ZH -> zh; Language.EN -> en; Language.JA -> ja }
    fun category(category: Category) = when (category) {
        Category.DAILY -> text("日常", "Daily", "日常")
        Category.WORK -> text("工作", "Work", "仕事")
        Category.HOBBIES -> text("兴趣", "Hobbies", "趣味")
    }
    fun themeColor(color: ThemeColor) = when (color) {
        ThemeColor.GREEN -> text("森林绿", "Forest", "グリーン")
        ThemeColor.BLUE -> text("海洋蓝", "Ocean", "ブルー")
        ThemeColor.PURPLE -> text("鸢尾紫", "Iris", "パープル")
        ThemeColor.ORANGE -> text("暖橘色", "Amber", "オレンジ")
        ThemeColor.ROSE -> text("玫瑰粉", "Rose", "ローズ")
        ThemeColor.GRAPHITE -> text("石墨灰", "Graphite", "グレー")
    }
    val all get() = text("全部笔记", "All notes", "すべてのノート")
    val newNote get() = text("新建笔记", "New note", "新規ノート")
    val save get() = text("保存", "Save", "保存")
    val settings get() = text("设置", "Settings", "設定")
    val cancel get() = text("取消", "Cancel", "キャンセル")
    val delete get() = text("删除", "Delete", "削除")
    val import get() = text("导入 TXT", "Import TXT", "TXT 読み込み")
    val backup get() = text("备份", "Backup", "バックアップ")
    val restore get() = text("恢复备份", "Restore backup", "バックアップ復元")
    val export get() = text("导出 TXT", "Export TXT", "TXT 書き出し")
    val merge get() = text("合并导出", "Export merged", "結合して書き出し")
    val copy get() = text("复制", "Copy", "コピー")
    val paste get() = text("粘贴", "Paste", "貼り付け")
    val attach get() = text("附加图片", "Attach image", "画像を添付")
    val share get() = text("分享", "Share", "共有")
    val pin get() = text("置顶", "Pin", "固定")
    val unpin get() = text("取消置顶", "Unpin", "固定解除")
    val saved get() = text("已保存", "Saved", "保存しました")
    val draftSaved get() = text("草稿已保留", "Draft retained", "下書き保存済み")
    val filename get() = text("笔记名称", "File name", "ファイル名")
    val search get() = text("搜索笔记…", "Search notes…", "ノートを検索…")
    val fullText get() = text("全文搜索", "Search content", "全文検索")
    val emptyTitle get() = text("给想法一个落脚的地方", "A little space for your thoughts", "思いつきを書き留める場所")
    val emptyBody get() = text("写下日常、工作和兴趣。每一篇笔记，都能带走为纯文本。", "Capture daily life, work, and everything in between. Your notes stay yours, in plain text.", "日常、仕事、趣味を記録。ノートはいつでもテキストで持ち出せます。")
    val discardTitle get() = text("保存这次修改？", "Save your changes?", "変更を保存しますか？")
    val discardBody get() = text("切换笔记前，请保存或放弃当前修改。", "Save or discard the current draft before switching notes.", "切り替える前に変更を保存するか破棄してください。")
    val discard get() = text("放弃修改", "Discard changes", "変更を破棄")
    val deleteBody get() = text("删除后无法撤销。建议先导出备份。", "Deletion cannot be undone. Export a backup first if needed.", "削除は取り消せません。必要なら先にバックアップしてください。")
}
