# -*- coding: utf-8 -*-
import sys
import re
import ctypes
import time
from ctypes import wintypes

from PySide6.QtCore import Qt, QObject, Signal, QRunnable, QThreadPool, QPoint, QEvent
from PySide6.QtGui import QColor, QPainter, QLinearGradient, QRadialGradient, QIcon, QPixmap, QFont, QShortcut, QKeySequence
from PySide6.QtWidgets import (
    QApplication, QWidget, QFrame, QLabel, QPushButton, QTextEdit, QComboBox,
    QVBoxLayout, QHBoxLayout, QGridLayout, QGraphicsDropShadowEffect, QMessageBox,
    QLineEdit, QSizePolicy
)

from deep_translator import GoogleTranslator\nimport translators as ts

APP_NAME = "中英文互译"
_TRANSLATION_CACHE = {}
_CACHE_LIMIT = 200


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
        key = (self.text, self.source, self.target)
        cached = _TRANSLATION_CACHE.get(key)
        if cached is not None:
            self.signals.finished.emit(cached)
            return

        try:
            source = self.source
            if source == "auto":
                source = "zh-CN" if has_chinese(self.text) else "en"

            bing_source = "zh" if source in ("zh", "zh-CN", "zh-cn") else source
            bing_target = "zh" if self.target in ("zh", "zh-CN", "zh-cn") else self.target

            google_source = "zh-CN" if bing_source == "zh" else bing_source
            google_target = "zh-CN" if bing_target == "zh" else bing_target

            result = []
            last_error = None

            for part in split_text(self.text):
                translated = None

                # 主引擎：Bing。避免 Google 网页端的频率限制。
                try:
                    translated = ts.translate_text(
                        part,
                        translator="bing",
                        from_language=bing_source,
                        to_language=bing_target,
                    )
                    if not isinstance(translated, str) or not translated.strip():
                        translated = None
                except Exception as e:
                    last_error = e
                    translated = None

                # 备用引擎：Google。只有 Bing 暂时不可用时才调用。
                if translated is None:
                    try:
                        time.sleep(0.45)
                        translated = GoogleTranslator(
                            source=google_source,
                            target=google_target
                        ).translate(part)
                    except Exception as e:
                        last_error = e
                        translated = None

                if translated is None:
                    raise RuntimeError("translation_service_busy") from last_error

                result.append(translated)
                if len(self.text) > 3500:
                    time.sleep(0.35)

            final = "\n".join(result)

            if len(_TRANSLATION_CACHE) >= _CACHE_LIMIT:
                try:
                    _TRANSLATION_CACHE.pop(next(iter(_TRANSLATION_CACHE)))
                except Exception:
                    _TRANSLATION_CACHE.clear()
            _TRANSLATION_CACHE[key] = final

            self.signals.finished.emit(final)
        except Exception:
            self.signals.error.emit("翻译服务暂时繁忙，请稍后再试。")


class GlassBackdrop(QWidget):
    def paintEvent(self, event):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        r = self.rect()

        g = QLinearGradient(0, 0, r.width(), r.height())
        g.setColorAt(0.00, QColor("#eef7ff"))
        g.setColorAt(0.30, QColor("#f5efff"))
        g.setColorAt(0.62, QColor("#fff2f7"))
        g.setColorAt(1.00, QColor("#edfbf6"))
        p.fillRect(r, g)

        scale = max(0.55, min(1.3, r.width() / 680))
        blobs = [
            (0.10, 0.08, 170 * scale, QColor(89, 145, 255, 78)),
            (0.86, 0.15, 200 * scale, QColor(175, 111, 255, 68)),
            (0.76, 0.90, 220 * scale, QColor(255, 116, 171, 56)),
            (0.12, 0.90, 190 * scale, QColor(75, 218, 185, 54)),
        ]
        for x, y, size, color in blobs:
            cx, cy = r.width() * x, r.height() * y
            rg = QRadialGradient(cx, cy, size)
            rg.setColorAt(0, color)
            rg.setColorAt(1, QColor(color.red(), color.green(), color.blue(), 0))
            p.setBrush(rg)
            p.setPen(Qt.NoPen)
            p.drawEllipse(int(cx-size), int(cy-size), int(size*2), int(size*2))


class ResizableFrameless(GlassBackdrop):
    EDGE = 7

    def __init__(self):
        super().__init__()
        self._drag_pos = QPoint()
        self.setMouseTracking(True)
        self.setAttribute(Qt.WA_TranslucentBackground, True)

    def _resize_edges(self, pos):
        x, y = pos.x(), pos.y()
        w, h = self.width(), self.height()
        edges = Qt.Edges()
        if x <= self.EDGE:
            edges |= Qt.LeftEdge
        elif x >= w - self.EDGE:
            edges |= Qt.RightEdge
        if y <= self.EDGE:
            edges |= Qt.TopEdge
        elif y >= h - self.EDGE:
            edges |= Qt.BottomEdge
        return edges

    def mouseMoveEvent(self, event):
        edges = self._resize_edges(event.position())
        if not (event.buttons() & Qt.LeftButton):
            if edges in (Qt.LeftEdge | Qt.TopEdge, Qt.RightEdge | Qt.BottomEdge):
                self.setCursor(Qt.SizeFDiagCursor)
            elif edges in (Qt.RightEdge | Qt.TopEdge, Qt.LeftEdge | Qt.BottomEdge):
                self.setCursor(Qt.SizeBDiagCursor)
            elif edges & (Qt.LeftEdge | Qt.RightEdge):
                self.setCursor(Qt.SizeHorCursor)
            elif edges & (Qt.TopEdge | Qt.BottomEdge):
                self.setCursor(Qt.SizeVerCursor)
            else:
                self.unsetCursor()
        elif not self._drag_pos.isNull():
            self.move(event.globalPosition().toPoint() - self._drag_pos)
            event.accept()
            return
        super().mouseMoveEvent(event)

    def mousePressEvent(self, event):
        if event.button() == Qt.LeftButton:
            edges = self._resize_edges(event.position())
            if edges and self.windowHandle():
                self.windowHandle().startSystemResize(edges)
                event.accept()
                return
            if event.position().y() < 62:
                self._drag_pos = event.globalPosition().toPoint() - self.frameGeometry().topLeft()
                event.accept()
                return
        super().mousePressEvent(event)

    def mouseReleaseEvent(self, event):
        self._drag_pos = QPoint()
        super().mouseReleaseEvent(event)

    def _apply_windows_glass(self):
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

    def showEvent(self, event):
        super().showEvent(event)
        self._apply_windows_glass()


class FloatingTranslator(ResizableFrameless):
    request_main = Signal()

    def __init__(self, thread_pool):
        super().__init__()
        self.thread_pool = thread_pool
        self.setWindowTitle("悬浮翻译")
        self.setWindowFlags(Qt.FramelessWindowHint | Qt.Tool | Qt.WindowStaysOnTopHint)
        self.resize(196, 116)
        self.setMinimumSize(156, 94)
        self.setMaximumSize(420, 260)
        self._build_ui()

    def _build_ui(self):
        outer = QVBoxLayout(self)
        outer.setContentsMargins(8, 7, 8, 8)
        outer.setSpacing(5)

        bar = QHBoxLayout()
        bar.setSpacing(4)
        self.mode_label = QLabel("中 ⇄ 英")
        self.mode_label.setObjectName("FloatTitle")
        bar.addWidget(self.mode_label)
        bar.addStretch()

        expand = QPushButton("▣")
        expand.setToolTip("打开主界面")
        expand.setObjectName("FloatIconButton")
        expand.clicked.connect(self._back_main)
        close = QPushButton("×")
        close.setToolTip("关闭悬浮窗")
        close.setObjectName("FloatIconButton")
        close.clicked.connect(self.close)
        bar.addWidget(expand)
        bar.addWidget(close)
        outer.addLayout(bar)

        self.input_line = QLineEdit()
        self.input_line.setPlaceholderText("输入并回车翻译")
        self.input_line.setObjectName("FloatInput")
        self.input_line.returnPressed.connect(self.translate)
        outer.addWidget(self.input_line)

        bottom = QHBoxLayout()
        bottom.setSpacing(5)
        self.output_label = QLabel("译文")
        self.output_label.setObjectName("FloatOutput")
        self.output_label.setTextInteractionFlags(Qt.TextSelectableByMouse)
        self.output_label.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        self.output_label.setWordWrap(False)
        bottom.addWidget(self.output_label, 1)

        go = QPushButton("译")
        go.setObjectName("FloatGo")
        go.setToolTip("翻译")
        go.clicked.connect(self.translate)
        bottom.addWidget(go)
        outer.addLayout(bottom, 1)

        self.setStyleSheet(r'''
            * { font-family: "Microsoft YaHei UI", "Segoe UI"; color: #1e2940; }
            QLabel#FloatTitle { font-size: 10px; font-weight: 800; color: #5a5b78; }
            QPushButton#FloatIconButton {
                min-width: 20px; max-width: 20px; min-height: 20px; max-height: 20px;
                padding: 0; border: none; border-radius: 7px;
                background: rgba(255,255,255,125); color: #5f6680; font-size: 11px;
            }
            QPushButton#FloatIconButton:hover { background: rgba(255,255,255,210); }
            QLineEdit#FloatInput {
                min-height: 28px; border-radius: 10px;
                padding: 0 9px; border: 1px solid rgba(255,255,255,220);
                background: rgba(255,255,255,150); font-size: 11px;
            }
            QLabel#FloatOutput {
                min-height: 26px; border-radius: 9px;
                padding: 4px 8px; background: rgba(255,255,255,112);
                border: 1px solid rgba(255,255,255,175); font-size: 10px;
            }
            QPushButton#FloatGo {
                min-width: 28px; max-width: 28px; min-height: 28px; max-height: 28px;
                padding: 0; border: none; border-radius: 10px;
                color: white; font-weight: 800; font-size: 11px;
                background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 #6778ff, stop:.52 #9667ee, stop:1 #ec74ac);
            }
        ''')

    def paintEvent(self, event):
        super().paintEvent(event)
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        p.setPen(QColor(255,255,255,185))
        p.setBrush(QColor(255,255,255,110))
        p.drawRoundedRect(self.rect().adjusted(1,1,-1,-1), 20, 20)

    def _back_main(self):
        self.request_main.emit()
        self.hide()

    def set_seed(self, source_text="", result_text=""):
        if source_text:
            self.input_line.setText(source_text.replace("\n", " ")[:300])
        if result_text:
            one = result_text.replace("\n", " ")
            self.output_label.setText(one[:120])
            self.output_label.setToolTip(result_text)

    def translate(self):
        text = self.input_line.text().strip()
        if not text:
            return
        source = "zh-CN" if has_chinese(text) else "en"
        target = "en" if source == "zh-CN" else "zh-CN"
        self.mode_label.setText("中 → 英" if source == "zh-CN" else "英 → 中")
        self.output_label.setText("翻译中…")
        worker = TranslateWorker(text, source, target)
        worker.signals.finished.connect(self._done)
        worker.signals.error.connect(self._error)
        self.thread_pool.start(worker)

    def _done(self, result):
        one = result.replace("\n", " ")
        self.output_label.setText(one)
        self.output_label.setToolTip(result)
        QApplication.clipboard().setText(result)

    def _error(self, message):
        self.output_label.setText("服务繁忙")
        self.output_label.setToolTip(message[:300])

    def mouseDoubleClickEvent(self, event):
        if event.button() == Qt.LeftButton:
            self._back_main()
            event.accept()
            return
        super().mouseDoubleClickEvent(event)


class TranslatorWindow(ResizableFrameless):
    def __init__(self):
        super().__init__()
        self.setWindowTitle(APP_NAME)
        self.resize(560, 360)
        self.setMinimumSize(430, 300)
        self.setWindowFlags(Qt.FramelessWindowHint | Qt.Window)
        self.thread_pool = QThreadPool.globalInstance()
        self.float_window = FloatingTranslator(self.thread_pool)
        self.float_window.request_main.connect(self.restore_from_float)
        self.compact_layout = False
        self._build_ui()

    def _glass_shadow(self, widget, blur=24, alpha=34, y=6):
        effect = QGraphicsDropShadowEffect(widget)
        effect.setBlurRadius(blur)
        effect.setOffset(0, y)
        effect.setColor(QColor(47, 62, 91, alpha))
        widget.setGraphicsEffect(effect)

    def _build_ui(self):
        self.outer = QVBoxLayout(self)
        self.outer.setContentsMargins(12, 10, 12, 12)
        self.outer.setSpacing(8)

        titlebar = QHBoxLayout()
        titlebar.setSpacing(5)
        brand = QLabel("译")
        brand.setObjectName("BrandMark")
        title_text = QLabel("中英文互译")
        title_text.setObjectName("Brand")
        titlebar.addWidget(brand)
        titlebar.addWidget(title_text)
        titlebar.addStretch()

        self.float_btn = QPushButton("悬浮")
        self.float_btn.setObjectName("FloatButton")
        self.float_btn.clicked.connect(self.to_float)

        min_btn = QPushButton("—")
        close_btn = QPushButton("×")
        min_btn.setObjectName("TitleButton")
        close_btn.setObjectName("CloseButton")
        min_btn.clicked.connect(self.showMinimized)
        close_btn.clicked.connect(self.close)
        titlebar.addWidget(self.float_btn)
        titlebar.addWidget(min_btn)
        titlebar.addWidget(close_btn)
        self.outer.addLayout(titlebar)

        control = QFrame()
        control.setObjectName("GlassCard")
        self._glass_shadow(control, 18, 26, 4)
        c = QHBoxLayout(control)
        c.setContentsMargins(10, 7, 10, 7)
        c.setSpacing(6)

        self.source_box = QComboBox()
        self.source_box.addItems(["自动检测", "中文", "英文"])
        self.target_box = QComboBox()
        self.target_box.addItems(["英文", "中文"])
        swap = QPushButton("⇄")
        swap.setObjectName("SwapButton")
        swap.clicked.connect(self.swap_languages)
        clear = QPushButton("清空")
        clear.setObjectName("SoftButton")
        clear.clicked.connect(self.clear_all)
        c.addWidget(self.source_box)
        c.addWidget(swap)
        c.addWidget(self.target_box)
        c.addStretch()
        c.addWidget(clear)
        self.outer.addWidget(control)

        self.editor_grid = QGridLayout()
        self.editor_grid.setSpacing(8)
        self.editor_grid.setColumnStretch(0, 1)
        self.editor_grid.setColumnStretch(1, 1)
        self.editor_grid.setRowStretch(0, 1)

        self.left_card, self.input_edit, self.left_footer = self._editor_card("原文", "输入中文或英文…", True)
        self.right_card, self.output_edit, self.right_footer = self._editor_card("译文", "翻译结果", False)
        self.editor_grid.addWidget(self.left_card, 0, 0)
        self.editor_grid.addWidget(self.right_card, 0, 1)
        self.outer.addLayout(self.editor_grid, 1)

        paste_btn = QPushButton("粘贴")
        paste_btn.setObjectName("TinyButton")
        paste_btn.clicked.connect(self.paste_text)
        self.count_label = QLabel("0 字")
        self.count_label.setObjectName("Muted")
        self.left_footer.addWidget(paste_btn)
        self.left_footer.addStretch()
        self.left_footer.addWidget(self.count_label)

        copy_btn = QPushButton("复制")
        copy_btn.setObjectName("TinyButton")
        copy_btn.clicked.connect(self.copy_result)
        self.right_footer.addWidget(copy_btn)
        self.right_footer.addStretch()

        action = QHBoxLayout()
        action.setSpacing(7)
        self.status = QLabel("Ctrl + Enter 快速翻译")
        self.status.setObjectName("Muted")
        action.addWidget(self.status)
        action.addStretch()
        self.translate_btn = QPushButton("翻译")
        self.translate_btn.setObjectName("PrimaryButton")
        self.translate_btn.clicked.connect(self.translate)
        action.addWidget(self.translate_btn)
        self.outer.addLayout(action)

        self.input_edit.textChanged.connect(self.update_count)
        QShortcut(QKeySequence("Ctrl+Return"), self, activated=self.translate)
        QShortcut(QKeySequence("Ctrl+Enter"), self, activated=self.translate)
        QShortcut(QKeySequence("Escape"), self, activated=self.clear_all)
        self._set_styles()

    def _editor_card(self, title, placeholder, editable=True):
        card = QFrame()
        card.setObjectName("GlassCard")
        self._glass_shadow(card)
        v = QVBoxLayout(card)
        v.setContentsMargins(10, 9, 10, 8)
        v.setSpacing(5)
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
        footer.setSpacing(5)
        v.addLayout(footer)
        return card, edit, footer

    def _set_styles(self):
        self.setStyleSheet(r'''
            * { font-family: "Microsoft YaHei UI", "Segoe UI"; color: #172033; }
            QLabel#BrandMark {
                min-width: 30px; max-width: 30px; min-height: 30px; max-height: 30px;
                border-radius: 10px; color: white; font-size: 15px; font-weight: 900;
                qproperty-alignment: AlignCenter;
                background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 #6678ff, stop:.55 #9a65ec, stop:1 #eb73ad);
            }
            QLabel#Brand { font-size: 15px; font-weight: 850; color: #20283a; }
            QLabel#SectionTitle { font-size: 11px; font-weight: 750; color: #3f4960; }
            QLabel#Muted { font-size: 9px; color: rgba(52,62,84,150); }
            QFrame#GlassCard {
                background: rgba(255,255,255,165);
                border: 1px solid rgba(255,255,255,220);
                border-radius: 17px;
            }
            QTextEdit#Editor {
                background: rgba(255,255,255,110);
                border: 1px solid rgba(255,255,255,190);
                border-radius: 12px;
                padding: 7px; font-size: 12px;
                selection-background-color: rgba(86,116,255,88);
            }
            QTextEdit#Editor:focus {
                border: 1px solid rgba(92,112,255,125);
                background: rgba(255,255,255,150);
            }
            QComboBox {
                min-height: 29px; min-width: 82px;
                padding: 0 8px; border-radius: 10px;
                border: 1px solid rgba(255,255,255,215);
                background: rgba(255,255,255,145);
                font-size: 10px; font-weight: 650;
            }
            QComboBox::drop-down { border: none; width: 20px; }
            QComboBox QAbstractItemView {
                background: white; border: 1px solid #e6e9ef;
                selection-background-color: #e9efff; padding: 4px;
            }
            QPushButton {
                border: none; border-radius: 10px; min-height: 29px;
                padding: 0 9px; font-size: 10px; font-weight: 700;
            }
            QPushButton#SoftButton, QPushButton#TinyButton, QPushButton#FloatButton {
                background: rgba(255,255,255,135);
                border: 1px solid rgba(255,255,255,205);
            }
            QPushButton#TinyButton { min-height: 23px; padding: 0 8px; font-size: 9px; }
            QPushButton#FloatButton { min-width: 46px; }
            QPushButton#SoftButton:hover, QPushButton#TinyButton:hover, QPushButton#FloatButton:hover {
                background: rgba(255,255,255,205);
            }
            QPushButton#SwapButton {
                min-width: 30px; max-width: 30px; padding: 0;
                background: rgba(118,111,255,35); color: #5b56db;
                font-size: 14px; border: 1px solid rgba(255,255,255,205);
            }
            QPushButton#PrimaryButton {
                min-width: 74px; min-height: 31px;
                color: white; font-size: 10px; font-weight: 850;
                background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 #6676ff, stop:.52 #8c63ee, stop:1 #e164af);
            }
            QPushButton#PrimaryButton:disabled { background: rgba(128,136,160,105); }
            QPushButton#TitleButton, QPushButton#CloseButton {
                min-width: 29px; max-width: 29px; min-height: 29px; max-height: 29px;
                padding: 0; border-radius: 10px; background: rgba(255,255,255,100);
                color: #59637a; font-size: 13px;
            }
            QPushButton#TitleButton:hover { background: rgba(255,255,255,190); }
            QPushButton#CloseButton:hover { background: rgba(255,91,106,170); color: white; }
            QScrollBar:vertical { background: transparent; width: 6px; margin: 3px; }
            QScrollBar::handle:vertical { background: rgba(92,101,124,70); border-radius: 3px; min-height: 24px; }
            QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical { height: 0; }
        ''')

    def resizeEvent(self, event):
        super().resizeEvent(event)
        compact = self.width() < 515
        if compact != self.compact_layout:
            self.compact_layout = compact
            self.editor_grid.removeWidget(self.left_card)
            self.editor_grid.removeWidget(self.right_card)
            if compact:
                self.editor_grid.addWidget(self.left_card, 0, 0)
                self.editor_grid.addWidget(self.right_card, 1, 0)
                self.editor_grid.setRowStretch(0, 1)
                self.editor_grid.setRowStretch(1, 1)
                self.editor_grid.setColumnStretch(0, 1)
            else:
                self.editor_grid.addWidget(self.left_card, 0, 0)
                self.editor_grid.addWidget(self.right_card, 0, 1)
                self.editor_grid.setColumnStretch(0, 1)
                self.editor_grid.setColumnStretch(1, 1)

    def update_count(self):
        self.count_label.setText(f"{len(self.input_edit.toPlainText())} 字")

    def paste_text(self):
        self.input_edit.insertPlainText(QApplication.clipboard().text())

    def copy_result(self):
        text = self.output_edit.toPlainText().strip()
        if text:
            QApplication.clipboard().setText(text)
            self.status.setText("已复制")

    def clear_all(self):
        self.input_edit.clear()
        self.output_edit.clear()
        self.status.setText("Ctrl + Enter 快速翻译")

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
            return
        src_label = self.source_box.currentText()
        dst_label = self.target_box.currentText()
        source = "auto" if src_label == "自动检测" else ("zh-CN" if src_label == "中文" else "en")
        target = "zh-CN" if dst_label == "中文" else "en"
        if source != "auto" and source == target:
            self.status.setText("请选择不同语言")
            return

        self.translate_btn.setEnabled(False)
        self.translate_btn.setText("翻译中")
        self.status.setText("正在翻译…")
        worker = TranslateWorker(text, source, target)
        worker.signals.finished.connect(self.translation_done)
        worker.signals.error.connect(self.translation_error)
        self.thread_pool.start(worker)

    def translation_done(self, result):
        self.output_edit.setPlainText(result)
        self.translate_btn.setEnabled(True)
        self.translate_btn.setText("翻译")
        self.status.setText("完成")

    def translation_error(self, message):
        self.translate_btn.setEnabled(True)
        self.translate_btn.setText("翻译")
        self.status.setText("网络错误")
        QMessageBox.warning(self, APP_NAME, "翻译失败，请检查网络连接。\n\n" + message[:300])

    def to_float(self):
        self.float_window.set_seed(self.input_edit.toPlainText(), self.output_edit.toPlainText())
        self.float_window.show()
        self.float_window.raise_()
        self.float_window.activateWindow()
        self.hide()

    def restore_from_float(self):
        self.show()
        self.raise_()
        self.activateWindow()

    def closeEvent(self, event):
        self.float_window.close()
        event.accept()


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
