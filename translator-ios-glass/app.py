# -*- coding: utf-8 -*-
import sys
import re
import ctypes
from ctypes import wintypes

from PySide6.QtCore import Qt, QObject, Signal, QRunnable, QThreadPool, QPoint
from PySide6.QtGui import QColor, QPainter, QLinearGradient, QRadialGradient, QIcon, QPixmap, QFont
from PySide6.QtWidgets import (
    QApplication, QWidget, QFrame, QLabel, QPushButton, QTextEdit, QComboBox,
    QVBoxLayout, QHBoxLayout, QGridLayout, QGraphicsDropShadowEffect, QMessageBox
)

from deep_translator import GoogleTranslator

APP_NAME = "中英文互译"


def has_chinese(text: str) -> bool:
    return bool(re.search(r"[\u4e00-\u9fff]", text))


def split_text(text: str, limit: int = 3500):
    if len(text) <= limit:
        return [text]
    out, buf = [], ""
    for line in text.splitlines():
        candidate = line if not buf else buf + "\n" + line
        if len(candidate) <= limit:
            buf = candidate
        else:
            if buf:
                out.append(buf)
            while len(line) > limit:
                out.append(line[:limit])
                line = line[limit:]
            buf = line
    if buf:
        out.append(buf)
    return out


class WorkerSignals(QObject):
    finished = Signal(str)
    error = Signal(str)


class TranslateWorker(QRunnable):
    def __init__(self, text, source, target):
        super().__init__()
        self.text = text
        self.source = source
        self.target = target
        self.signals = WorkerSignals()

    def run(self):
        try:
            translator = GoogleTranslator(source=self.source, target=self.target)
            result = []
            for part in split_text(self.text):
                result.append(translator.translate(part))
            self.signals.finished.emit("\n".join(result))
        except Exception as e:
            self.signals.error.emit(str(e))


class GradientBackdrop(QWidget):
    def paintEvent(self, event):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        r = self.rect()

        g = QLinearGradient(0, 0, r.width(), r.height())
        g.setColorAt(0.00, QColor("#eef6ff"))
        g.setColorAt(0.32, QColor("#f8efff"))
        g.setColorAt(0.67, QColor("#fff4f8"))
        g.setColorAt(1.00, QColor("#effcf8"))
        p.fillRect(r, g)

        blobs = [
            (0.15, 0.12, 280, QColor(91, 141, 239, 88)),
            (0.84, 0.16, 320, QColor(190, 118, 255, 72)),
            (0.70, 0.82, 360, QColor(255, 119, 171, 60)),
            (0.18, 0.82, 320, QColor(77, 220, 187, 58)),
        ]
        for x, y, size, color in blobs:
            cx, cy = r.width() * x, r.height() * y
            rg = QRadialGradient(cx, cy, size)
            rg.setColorAt(0, color)
            rg.setColorAt(1, QColor(color.red(), color.green(), color.blue(), 0))
            p.setBrush(rg)
            p.setPen(Qt.NoPen)
            p.drawEllipse(int(cx-size), int(cy-size), size*2, size*2)


class TranslatorWindow(GradientBackdrop):
    def __init__(self):
        super().__init__()
        self.setWindowTitle(APP_NAME)
        self.resize(1120, 700)
        self.setMinimumSize(820, 560)
        self.setWindowFlags(Qt.FramelessWindowHint | Qt.Window)
        self.setAttribute(Qt.WA_TranslucentBackground, True)
        self.thread_pool = QThreadPool.globalInstance()
        self.drag_pos = QPoint()
        self._build_ui()

    def _glass_shadow(self, widget, blur=34, alpha=45, y=10):
        effect = QGraphicsDropShadowEffect(widget)
        effect.setBlurRadius(blur)
        effect.setOffset(0, y)
        effect.setColor(QColor(45, 62, 96, alpha))
        widget.setGraphicsEffect(effect)

    def _build_ui(self):
        outer = QVBoxLayout(self)
        outer.setContentsMargins(22, 18, 22, 22)
        outer.setSpacing(14)

        titlebar = QHBoxLayout()
        brand = QLabel("译 · Translate")
        brand.setObjectName("Brand")
        subtitle = QLabel("中英文智能互译")
        subtitle.setObjectName("Subtitle")
        brand_box = QVBoxLayout()
        brand_box.setSpacing(0)
        brand_box.addWidget(brand)
        brand_box.addWidget(subtitle)
        titlebar.addLayout(brand_box)
        titlebar.addStretch()

        self.min_btn = QPushButton("—")
        self.close_btn = QPushButton("×")
        self.min_btn.setObjectName("TitleButton")
        self.close_btn.setObjectName("CloseButton")
        self.min_btn.clicked.connect(self.showMinimized)
        self.close_btn.clicked.connect(self.close)
        titlebar.addWidget(self.min_btn)
        titlebar.addWidget(self.close_btn)
        outer.addLayout(titlebar)

        control_card = QFrame()
        control_card.setObjectName("GlassCard")
        self._glass_shadow(control_card, 28, 32, 8)
        control_layout = QHBoxLayout(control_card)
        control_layout.setContentsMargins(18, 14, 18, 14)
        control_layout.setSpacing(10)

        self.source_box = QComboBox()
        self.source_box.addItems(["自动检测", "中文", "英文"])
        self.target_box = QComboBox()
        self.target_box.addItems(["英文", "中文"])
        self.swap_btn = QPushButton("⇄")
        self.swap_btn.setObjectName("SwapButton")
        self.swap_btn.setToolTip("交换语言")
        self.swap_btn.clicked.connect(self.swap_languages)

        control_layout.addWidget(self.source_box)
        control_layout.addWidget(self.swap_btn)
        control_layout.addWidget(self.target_box)
        control_layout.addStretch()

        self.clear_btn = QPushButton("清空")
        self.clear_btn.setObjectName("SoftButton")
        self.clear_btn.clicked.connect(self.clear_all)
        control_layout.addWidget(self.clear_btn)
        outer.addWidget(control_card)

        grid = QGridLayout()
        grid.setSpacing(14)
        grid.setColumnStretch(0, 1)
        grid.setColumnStretch(1, 1)

        left = self._editor_card("原文", "输入中文或英文…", editable=True)
        right = self._editor_card("译文", "翻译结果会显示在这里", editable=False)
        self.input_edit = left[1]
        self.output_edit = right[1]
        self.left_footer = left[2]
        self.right_footer = right[2]
        grid.addWidget(left[0], 0, 0)
        grid.addWidget(right[0], 0, 1)
        outer.addLayout(grid, 1)

        paste_btn = QPushButton("粘贴")
        paste_btn.setObjectName("SoftButton")
        paste_btn.clicked.connect(self.paste_text)
        self.count_label = QLabel("0 字")
        self.count_label.setObjectName("Muted")
        self.left_footer.addWidget(paste_btn)
        self.left_footer.addStretch()
        self.left_footer.addWidget(self.count_label)

        copy_btn = QPushButton("复制译文")
        copy_btn.setObjectName("SoftButton")
        copy_btn.clicked.connect(self.copy_result)
        self.right_footer.addWidget(copy_btn)
        self.right_footer.addStretch()

        action_card = QFrame()
        action_card.setObjectName("GlassCard")
        self._glass_shadow(action_card, 28, 30, 7)
        action_layout = QHBoxLayout(action_card)
        action_layout.setContentsMargins(18, 12, 14, 12)

        self.status = QLabel("准备就绪  ·  Ctrl + Enter 快速翻译")
        self.status.setObjectName("Muted")
        action_layout.addWidget(self.status)
        action_layout.addStretch()

        self.translate_btn = QPushButton("翻 译")
        self.translate_btn.setObjectName("PrimaryButton")
        self.translate_btn.clicked.connect(self.translate)
        action_layout.addWidget(self.translate_btn)
        outer.addWidget(action_card)

        self.input_edit.textChanged.connect(self.update_count)
        self._install_shortcuts()
        self._set_styles()

    def _editor_card(self, title, placeholder, editable=True):
        card = QFrame()
        card.setObjectName("GlassCard")
        self._glass_shadow(card)
        v = QVBoxLayout(card)
        v.setContentsMargins(18, 16, 18, 14)
        v.setSpacing(10)
        label = QLabel(title)
        label.setObjectName("SectionTitle")
        v.addWidget(label)
        edit = QTextEdit()
        edit.setObjectName("Editor")
        edit.setPlaceholderText(placeholder)
        edit.setAcceptRichText(False)
        if not editable:
            edit.setReadOnly(True)
        v.addWidget(edit, 1)
        footer = QHBoxLayout()
        v.addLayout(footer)
        return card, edit, footer

    def _set_styles(self):
        self.setStyleSheet(r'''
            * { font-family: "Microsoft YaHei UI", "Segoe UI"; color: #172033; }
            QLabel#Brand { font-size: 23px; font-weight: 800; color: #111827; }
            QLabel#Subtitle { font-size: 12px; color: rgba(40,52,73,150); margin-top: 1px; }
            QLabel#SectionTitle { font-size: 14px; font-weight: 700; color: #26334a; }
            QLabel#Muted { font-size: 12px; color: rgba(42,54,78,155); }
            QFrame#GlassCard {
                background: rgba(255,255,255,178);
                border: 1px solid rgba(255,255,255,210);
                border-radius: 24px;
            }
            QTextEdit#Editor {
                background: rgba(255,255,255,116);
                border: 1px solid rgba(255,255,255,190);
                border-radius: 18px;
                padding: 14px;
                font-size: 16px;
                selection-background-color: rgba(79,124,255,90);
            }
            QTextEdit#Editor:focus {
                border: 1px solid rgba(86,116,255,130);
                background: rgba(255,255,255,150);
            }
            QComboBox {
                min-height: 40px; min-width: 126px;
                padding: 0 14px;
                border-radius: 14px;
                border: 1px solid rgba(255,255,255,210);
                background: rgba(255,255,255,150);
                font-size: 13px; font-weight: 600;
            }
            QComboBox::drop-down { border: none; width: 28px; }
            QComboBox QAbstractItemView {
                background: white; border: 1px solid #e6e9ef;
                selection-background-color: #e9efff; padding: 6px;
            }
            QPushButton {
                border: none; border-radius: 14px; min-height: 38px;
                padding: 0 15px; font-size: 13px; font-weight: 650;
            }
            QPushButton#SoftButton {
                background: rgba(255,255,255,145);
                border: 1px solid rgba(255,255,255,205);
            }
            QPushButton#SoftButton:hover { background: rgba(255,255,255,205); }
            QPushButton#SwapButton {
                min-width: 42px; max-width: 42px; padding: 0;
                background: rgba(118,111,255,35); color: #5b56db;
                font-size: 19px; border: 1px solid rgba(255,255,255,205);
            }
            QPushButton#SwapButton:hover { background: rgba(118,111,255,60); }
            QPushButton#PrimaryButton {
                min-width: 132px; min-height: 44px;
                border-radius: 16px;
                color: white; font-size: 14px; font-weight: 800;
                background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 #6676ff, stop:.52 #8c63ee, stop:1 #e164af);
            }
            QPushButton#PrimaryButton:disabled { background: rgba(128,136,160,100); }
            QPushButton#TitleButton, QPushButton#CloseButton {
                min-width: 38px; max-width: 38px; min-height: 34px; max-height: 34px;
                padding: 0; border-radius: 12px; background: rgba(255,255,255,100);
                color: #536078; font-size: 17px;
            }
            QPushButton#TitleButton:hover { background: rgba(255,255,255,190); }
            QPushButton#CloseButton:hover { background: rgba(255,91,106,170); color: white; }
            QScrollBar:vertical { background: transparent; width: 8px; margin: 4px; }
            QScrollBar::handle:vertical { background: rgba(92,101,124,80); border-radius: 4px; min-height: 28px; }
            QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical { height: 0; }
        ''')

    def _install_shortcuts(self):
        from PySide6.QtGui import QShortcut, QKeySequence
        QShortcut(QKeySequence("Ctrl+Return"), self, activated=self.translate)
        QShortcut(QKeySequence("Ctrl+Enter"), self, activated=self.translate)
        QShortcut(QKeySequence("Escape"), self, activated=self.clear_all)

    def update_count(self):
        self.count_label.setText(f"{len(self.input_edit.toPlainText())} 字")

    def paste_text(self):
        cb = QApplication.clipboard()
        self.input_edit.insertPlainText(cb.text())

    def copy_result(self):
        text = self.output_edit.toPlainText().strip()
        if text:
            QApplication.clipboard().setText(text)
            self.status.setText("译文已复制到剪贴板")

    def clear_all(self):
        self.input_edit.clear()
        self.output_edit.clear()
        self.status.setText("准备就绪  ·  Ctrl + Enter 快速翻译")

    def swap_languages(self):
        src = self.source_box.currentText()
        dst = self.target_box.currentText()
        if src == "自动检测":
            sample = self.input_edit.toPlainText().strip()
            src = "中文" if (sample and has_chinese(sample)) else ("英文" if sample else ("中文" if dst == "英文" else "英文"))
        self.source_box.setCurrentText(dst)
        self.target_box.setCurrentText(src)
        if self.output_edit.toPlainText().strip():
            a = self.input_edit.toPlainText()
            b = self.output_edit.toPlainText()
            self.input_edit.setPlainText(b)
            self.output_edit.setPlainText(a)

    def translate(self):
        text = self.input_edit.toPlainText().strip()
        if not text:
            QMessageBox.information(self, APP_NAME, "请先输入需要翻译的内容。")
            return

        src_label = self.source_box.currentText()
        dst_label = self.target_box.currentText()
        if src_label == "自动检测":
            source = "auto"
        else:
            source = "zh-CN" if src_label == "中文" else "en"
        target = "zh-CN" if dst_label == "中文" else "en"

        if source != "auto" and source == target:
            QMessageBox.information(self, APP_NAME, "源语言与目标语言相同，请切换翻译方向。")
            return

        self.translate_btn.setEnabled(False)
        self.translate_btn.setText("翻译中…")
        self.status.setText("正在联网翻译，请稍候…")

        worker = TranslateWorker(text, source, target)
        worker.signals.finished.connect(self.translation_done)
        worker.signals.error.connect(self.translation_error)
        self.thread_pool.start(worker)

    def translation_done(self, result):
        self.output_edit.setPlainText(result)
        self.translate_btn.setEnabled(True)
        self.translate_btn.setText("翻 译")
        self.status.setText("翻译完成")

    def translation_error(self, message):
        self.translate_btn.setEnabled(True)
        self.translate_btn.setText("翻 译")
        self.status.setText("翻译失败，请检查网络")
        QMessageBox.warning(self, APP_NAME, "翻译失败。请检查网络连接后重试。\n\n" + message[:500])

    def mousePressEvent(self, event):
        if event.button() == Qt.LeftButton and event.position().y() < 80:
            self.drag_pos = event.globalPosition().toPoint() - self.frameGeometry().topLeft()
            event.accept()

    def mouseMoveEvent(self, event):
        if event.buttons() & Qt.LeftButton and not self.drag_pos.isNull():
            self.move(event.globalPosition().toPoint() - self.drag_pos)
            event.accept()

    def mouseReleaseEvent(self, event):
        self.drag_pos = QPoint()
        super().mouseReleaseEvent(event)

    def showEvent(self, event):
        super().showEvent(event)
        self._apply_acrylic()

    def _apply_acrylic(self):
        if sys.platform != "win32":
            return
        try:
            hwnd = int(self.winId())
            DWMWA_WINDOW_CORNER_PREFERENCE = 33
            DWMWCP_ROUND = 2
            value = ctypes.c_int(DWMWCP_ROUND)
            ctypes.windll.dwmapi.DwmSetWindowAttribute(
                wintypes.HWND(hwnd), DWMWA_WINDOW_CORNER_PREFERENCE,
                ctypes.byref(value), ctypes.sizeof(value)
            )
            DWMWA_SYSTEMBACKDROP_TYPE = 38
            DWMSBT_TRANSIENTWINDOW = 3
            backdrop = ctypes.c_int(DWMSBT_TRANSIENTWINDOW)
            ctypes.windll.dwmapi.DwmSetWindowAttribute(
                wintypes.HWND(hwnd), DWMWA_SYSTEMBACKDROP_TYPE,
                ctypes.byref(backdrop), ctypes.sizeof(backdrop)
            )
        except Exception:
            pass


def make_icon():
    pix = QPixmap(128, 128)
    pix.fill(Qt.transparent)
    p = QPainter(pix)
    p.setRenderHint(QPainter.Antialiasing)
    g = QLinearGradient(0, 0, 128, 128)
    g.setColorAt(0, QColor("#6676ff"))
    g.setColorAt(0.55, QColor("#9b63e8"))
    g.setColorAt(1, QColor("#ef72aa"))
    p.setBrush(g)
    p.setPen(Qt.NoPen)
    p.drawRoundedRect(8, 8, 112, 112, 28, 28)
    p.setPen(Qt.white)
    f = QFont("Microsoft YaHei UI", 48)
    f.setBold(True)
    p.setFont(f)
    p.drawText(pix.rect(), Qt.AlignCenter, "译")
    p.end()
    return QIcon(pix)


def main():
    app = QApplication(sys.argv)
    app.setApplicationName(APP_NAME)
    app.setWindowIcon(make_icon())
    app.setStyle("Fusion")
    w = TranslatorWindow()
    w.setWindowIcon(make_icon())
    w.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
