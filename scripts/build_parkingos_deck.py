from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE, MSO_CONNECTOR
from pptx.enum.dml import MSO_THEME_COLOR
from pptx.dml.color import RGBColor
from pptx.chart.data import CategoryChartData
from pptx.enum.chart import XL_CHART_TYPE, XL_LEGEND_POSITION, XL_LABEL_POSITION
from pptx.enum.text import MSO_AUTO_SIZE
from pptx.enum.dml import MSO_LINE_DASH_STYLE
from pathlib import Path


OUT = Path(__file__).resolve().parents[1] / "ParkingOS_Business_Case.pptx"

# ParkingOS dark theme tokens from DesignTokens.java.
BG = "0A0E27"
CARD = "141B2D"
SURFACE = "0F152A"
HOVER = "1A2332"
TEAL = "00D4AA"
TEAL_BRIGHT = "2DE2BC"
ORANGE = "FF6B35"
YELLOW = "FFB800"
RED = "FF4757"
GREEN = "2ED573"
BLUE = "3498DB"
PURPLE = "9B59B6"
TEXT = "E8ECF1"
MUTED = "7B8BA3"
HEADER_MUTED = "AAB6C8"
BORDER = "2A3548"
WHITE = "FFFFFF"

W, H = 13.333, 7.5
FONT = "Arial"


def rgb(hexv):
    return RGBColor.from_string(hexv)


def add_box(slide, x, y, w, h, fill=CARD, line=None, radius=True, transparency=0):
    shape_type = MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE
    shp = slide.shapes.add_shape(shape_type, Inches(x), Inches(y), Inches(w), Inches(h))
    shp.fill.solid()
    shp.fill.fore_color.rgb = rgb(fill)
    shp.fill.transparency = transparency
    shp.line.color.rgb = rgb(line or fill)
    shp.line.width = Pt(0.8)
    return shp


def add_line(slide, x1, y1, x2, y2, color=BORDER, width=1.2, dash=None):
    line = slide.shapes.add_connector(MSO_CONNECTOR.STRAIGHT, Inches(x1), Inches(y1), Inches(x2), Inches(y2))
    line.line.color.rgb = rgb(color)
    line.line.width = Pt(width)
    if dash:
        line.line.dash_style = dash
    return line


def add_text(slide, text, x, y, w, h, size=16, color=TEXT, bold=False, font=FONT,
             align=PP_ALIGN.LEFT, valign=MSO_ANCHOR.TOP, margin=0, italic=False,
             caps=False):
    tb = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = tb.text_frame
    tf.clear()
    tf.word_wrap = True
    tf.margin_left = Inches(margin)
    tf.margin_right = Inches(margin)
    tf.margin_top = Inches(margin)
    tf.margin_bottom = Inches(margin)
    tf.vertical_anchor = valign
    p = tf.paragraphs[0]
    p.alignment = align
    run = p.add_run()
    run.text = text.upper() if caps else text
    run.font.name = font
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.italic = italic
    run.font.color.rgb = rgb(color)
    return tb


def add_rich_text(slide, runs, x, y, w, h, size=16, color=TEXT, align=PP_ALIGN.LEFT,
                  valign=MSO_ANCHOR.TOP, margin=0):
    tb = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = tb.text_frame
    tf.clear(); tf.word_wrap = True
    tf.margin_left = Inches(margin); tf.margin_right = Inches(margin)
    tf.margin_top = Inches(margin); tf.margin_bottom = Inches(margin)
    tf.vertical_anchor = valign
    p = tf.paragraphs[0]; p.alignment = align
    for item in runs:
        run = p.add_run(); run.text = item[0]
        run.font.name = FONT; run.font.size = Pt(item[1] if len(item) > 1 else size)
        run.font.bold = item[2] if len(item) > 2 else False
        run.font.color.rgb = rgb(item[3] if len(item) > 3 else color)
    return tb


def add_bullets(slide, items, x, y, w, h, size=15, color=TEXT, bullet_color=TEAL, gap=8):
    tb = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = tb.text_frame; tf.clear(); tf.word_wrap = True
    tf.margin_left = 0; tf.margin_right = 0; tf.margin_top = 0; tf.margin_bottom = 0
    for i, item in enumerate(items):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.space_after = Pt(gap); p.level = 0
        p.text = "• " + item
        p.font.name = FONT; p.font.size = Pt(size); p.font.color.rgb = rgb(color)
    return tb


def add_title(slide, eyebrow, title, sub=None, dark=True):
    add_text(slide, eyebrow, 0.72, 0.38, 5.8, 0.24, size=10, color=TEAL, bold=True, caps=True)
    add_text(slide, title, 0.72, 0.72, 11.6, 0.62, size=28, color=TEXT if dark else BG, bold=True)
    if sub:
        add_text(slide, sub, 0.74, 1.42, 11.2, 0.34, size=12, color=HEADER_MUTED if dark else MUTED)


def add_footer(slide, text="ParkingOS · Business case · Source: implementation review"):
    add_line(slide, 0.72, 7.08, 12.6, 7.08, BORDER, 0.7)
    add_text(slide, text, 0.72, 7.18, 9.8, 0.16, size=8.5, color=MUTED)
    add_text(slide, str(len(slide.part.package.presentation_part.presentation.slides)), 12.12, 7.16, 0.5, 0.18, size=8.5, color=MUTED, align=PP_ALIGN.RIGHT)


def set_bg(slide, color=BG):
    fill = slide.background.fill; fill.solid(); fill.fore_color.rgb = rgb(color)


def add_status_dot(slide, x, y, color, label, value, w=1.6):
    slide.shapes.add_shape(MSO_SHAPE.OVAL, Inches(x), Inches(y+0.03), Inches(0.12), Inches(0.12)).fill.solid()
    dot = slide.shapes[-1]; dot.fill.fore_color.rgb = rgb(color); dot.line.fill.background()
    add_text(slide, label, x+0.2, y, w, 0.18, size=9, color=MUTED, caps=True)
    add_text(slide, value, x+0.2, y+0.19, w, 0.28, size=17, color=TEXT, bold=True)


def add_kpi(slide, x, y, w, label, value, note, accent=TEAL):
    add_box(slide, x, y, w, 1.04, CARD, BORDER)
    add_box(slide, x, y, 0.07, 1.04, accent, accent, radius=False)
    add_text(slide, label, x+0.24, y+0.15, w-0.35, 0.17, size=9, color=MUTED, bold=True, caps=True)
    add_text(slide, value, x+0.24, y+0.35, w-0.35, 0.35, size=25, color=accent, bold=True)
    add_text(slide, note, x+0.24, y+0.78, w-0.35, 0.16, size=9, color=HEADER_MUTED)


def add_chip(slide, text, x, y, w, color=TEAL, fill=None):
    add_box(slide, x, y, w, 0.28, fill or color, fill or color, radius=True, transparency=78 if not fill else 0)
    add_text(slide, text, x, y+0.055, w, 0.14, size=8.5, color=color if not fill else BG, bold=True, align=PP_ALIGN.CENTER, caps=True)


def add_cell(slide, x, y, w, h, text, fill, color=TEXT, size=10, bold=False, align=PP_ALIGN.CENTER):
    add_box(slide, x, y, w, h, fill, fill, radius=False)
    add_text(slide, text, x+0.06, y+0.03, w-0.12, h-0.06, size=size, color=color, bold=bold, align=align, valign=MSO_ANCHOR.MIDDLE)


def add_chart_style(chart, series_colors, show_legend=False):
    chart.has_legend = show_legend
    if show_legend:
        chart.legend.position = XL_LEGEND_POSITION.BOTTOM
        chart.legend.font.size = Pt(9)
        chart.legend.font.color.rgb = rgb(MUTED)
    chart.value_axis.has_major_gridlines = True
    chart.value_axis.major_gridlines.format.line.color.rgb = rgb(BORDER)
    chart.value_axis.format.line.color.rgb = rgb(BORDER)
    chart.value_axis.tick_labels.font.color.rgb = rgb(MUTED)
    chart.value_axis.tick_labels.font.size = Pt(9)
    chart.category_axis.format.line.color.rgb = rgb(BORDER)
    chart.category_axis.tick_labels.font.color.rgb = rgb(MUTED)
    chart.category_axis.tick_labels.font.size = Pt(9)
    for i, series in enumerate(chart.series):
        series.format.fill.solid(); series.format.fill.fore_color.rgb = rgb(series_colors[i % len(series_colors)])
        series.format.line.color.rgb = rgb(series_colors[i % len(series_colors)])


def build():
    prs = Presentation(); prs.slide_width = Inches(W); prs.slide_height = Inches(H)
    blank = prs.slide_layouts[6]

    # 1 — cover
    s = prs.slides.add_slide(blank); set_bg(s)
    add_box(s, 0.72, 0.68, 1.48, 0.32, TEAL, TEAL)
    add_text(s, "PARKINGOS", 0.72, 0.75, 1.48, 0.14, size=9, color=BG, bold=True, align=PP_ALIGN.CENTER, caps=True)
    add_text(s, "From manual parking\nto measurable control", 0.72, 1.52, 7.4, 1.35, size=35, color=TEXT, bold=True)
    add_text(s, "A business case for protecting revenue, reducing operational effort, and scaling garage operations.", 0.76, 3.1, 6.3, 0.62, size=17, color=HEADER_MUTED)
    add_chip(s, "OPERATIONS / REVENUE / CONTROL", 0.76, 4.08, 2.54, TEAL)
    # dashboard motif
    add_box(s, 8.05, 1.08, 4.55, 4.75, CARD, BORDER)
    add_text(s, "LIVE OPERATIONS", 8.38, 1.4, 2.2, 0.18, size=9, color=TEAL, bold=True, caps=True)
    add_text(s, "GARAGE A · 18:42", 10.75, 1.4, 1.45, 0.18, size=9, color=MUTED, align=PP_ALIGN.RIGHT)
    add_kpi(s, 8.38, 1.83, 1.84, "Available", "42", "spaces", GREEN)
    add_kpi(s, 10.42, 1.83, 1.84, "Occupied", "68", "spaces", ORANGE)
    add_kpi(s, 8.38, 3.03, 1.84, "Active tickets", "31", "in progress", BLUE)
    add_kpi(s, 10.42, 3.03, 1.84, "Revenue today", "$1,284", "completed", TEAL)
    # occupancy cells
    add_text(s, "OCCUPANCY MAP", 8.38, 4.26, 2.1, 0.18, size=9, color=MUTED, bold=True, caps=True)
    colors = [GREEN, GREEN, ORANGE, ORANGE, ORANGE, RED, GREEN, BLUE, GREEN, ORANGE, GREEN, GREEN, RED, ORANGE, GREEN, BLUE, GREEN, GREEN]
    for i, c in enumerate(colors):
        x = 8.38 + (i % 6) * 0.52; y = 4.62 + (i // 6) * 0.32
        add_box(s, x, y, 0.38, 0.19, c, c, radius=True)
    add_text(s, "Decision brief · dark-theme edition", 0.76, 6.73, 5.0, 0.18, size=9, color=MUTED)
    add_footer(s, "ParkingOS · Business case · Built from the current application workflows")

    # 2 — problem
    s = prs.slides.add_slide(blank); set_bg(s); add_title(s, "01 / The problem", "Manual parking creates four kinds of leakage", "When the operation lives in memory, paper, and disconnected tools, value disappears between arrival and closeout.")
    items = [
        ("CAPACITY", "Drivers wait while staff search for space.", ORANGE, "Slow throughput"),
        ("REVENUE", "Fees, payments, refunds, and closeout are harder to reconcile.", RED, "Missed or disputed value"),
        ("LABOR", "Attendants and managers re-enter the same facts across shifts.", YELLOW, "Rework"),
        ("CONTROL", "Managers lack one view across garages, tickets, and payments.", PURPLE, "Late decisions"),
    ]
    for i, (label, body, accent, impact) in enumerate(items):
        x = 0.78 + (i % 2) * 6.05; y = 2.12 + (i // 2) * 1.8
        add_box(s, x, y, 5.5, 1.42, CARD, BORDER)
        add_box(s, x+0.28, y+0.28, 0.55, 0.55, accent, accent)
        add_text(s, str(i+1).zfill(2), x+0.28, y+0.43, 0.55, 0.16, size=13, color=BG, bold=True, align=PP_ALIGN.CENTER)
        add_text(s, label, x+1.08, y+0.24, 3.8, 0.18, size=10, color=accent, bold=True, caps=True)
        add_text(s, body, x+1.08, y+0.52, 3.95, 0.48, size=15, color=TEXT, bold=True)
        add_chip(s, impact, x+1.08, y+1.08, 1.5, accent)
    add_text(s, "The business problem is not a lack of parking data. It is the cost of turning activity into a trusted operating decision.", 0.8, 6.04, 11.4, 0.45, size=16, color=TEAL_BRIGHT, bold=True)
    add_footer(s, "ParkingOS · Business case · Problem framing derived from operational workflows")

    # 3 — operating loop
    s = prs.slides.add_slide(blank); set_bg(s); add_title(s, "02 / The operating loop", "ParkingOS closes the loop from occupancy to revenue", "One operational record carries the vehicle from entry through payment, release, and reporting.")
    steps = [("01", "LIVE GARAGE", "Know what is available, occupied, reserved, or under maintenance.", BLUE), ("02", "TICKET", "Create a durable record for the parking session.", ORANGE), ("03", "PAYMENT", "Calculate, validate, complete, and protect the transaction.", TEAL), ("04", "REPORT", "Turn completed activity into revenue and occupancy insight.", GREEN)]
    for i, (num, title, body, accent) in enumerate(steps):
        x = 0.82 + i * 3.06
        add_box(s, x, 2.16, 2.5, 2.55, CARD, BORDER)
        add_box(s, x+0.24, 2.45, 0.52, 0.52, accent, accent)
        add_text(s, num, x+0.24, 2.61, 0.52, 0.15, size=12, color=BG, bold=True, align=PP_ALIGN.CENTER)
        add_text(s, title, x+0.24, 3.24, 2.0, 0.22, size=13, color=accent, bold=True, caps=True)
        add_text(s, body, x+0.24, 3.62, 1.95, 0.65, size=13, color=TEXT, bold=True)
        if i < 3:
            add_line(s, x+2.5, 3.44, x+2.98, 3.44, TEAL, 2)
            add_text(s, "›", x+2.67, 3.23, 0.2, 0.35, size=24, color=TEAL, bold=True, align=PP_ALIGN.CENTER)
    add_box(s, 0.82, 5.42, 11.7, 0.78, SURFACE, BORDER)
    add_rich_text(s, [("BUSINESS EFFECT  ", 10, True, TEAL), ("The same system of record supports throughput, revenue assurance, shift control, and management reporting.", 15, True, TEXT)], 1.08, 5.68, 11.1, 0.24, valign=MSO_ANCHOR.MIDDLE)
    add_footer(s, "ParkingOS · Business case · Evidence: live snapshots, ticket lifecycle, payment service, analytics/reporting")

    # 4 — revenue protection
    s = prs.slides.add_slide(blank); set_bg(s); add_title(s, "03 / Revenue protection", "Every completed ticket becomes a traceable financial event", "Revenue is calculated and recognized through the payment closeout path—not reconstructed later from memory.")
    add_box(s, 0.8, 2.0, 7.15, 3.9, CARD, BORDER)
    add_text(s, "TRANSACTION WATERFALL", 1.1, 2.3, 3.0, 0.18, size=10, color=TEAL, bold=True, caps=True)
    flow = [("Parking duration", "Hourly fee", BLUE), ("Base amount", "+ tax", YELLOW), ("Final amount", "Payment", TEAL), ("Completed", "Revenue report", GREEN)]
    for i, (a, b, c) in enumerate(flow):
        x = 1.08 + i * 1.62
        add_box(s, x, 3.03, 1.3, 1.18, SURFACE, c)
        add_text(s, a, x+0.12, 3.25, 1.06, 0.34, size=12, color=TEXT, bold=True, align=PP_ALIGN.CENTER, valign=MSO_ANCHOR.MIDDLE)
        add_text(s, b, x+0.12, 3.84, 1.06, 0.18, size=9, color=c, bold=True, align=PP_ALIGN.CENTER, caps=True)
        if i < 3:
            add_text(s, "→", x+1.34, 3.37, 0.28, 0.3, size=18, color=TEAL, bold=True, align=PP_ALIGN.CENTER)
    add_text(s, "Controls represented in the codebase", 1.1, 4.72, 3.0, 0.18, size=10, color=MUTED, bold=True, caps=True)
    add_bullets(s, ["Duplicate-payment protection", "Card, cash, and wallet payment paths", "Refund handling and status transitions", "Payment/ticket garage consistency"], 1.1, 5.02, 6.1, 0.7, size=12, gap=4)
    add_box(s, 8.35, 2.0, 4.17, 3.9, SURFACE, BORDER)
    add_text(s, "WHY IT MATTERS", 8.68, 2.3, 2.2, 0.18, size=10, color=ORANGE, bold=True, caps=True)
    add_text(s, "Manual closeout asks managers to trust a later reconstruction.", 8.68, 2.85, 3.25, 0.74, size=20, color=TEXT, bold=True)
    add_line(s, 8.68, 3.96, 11.9, 3.96, BORDER, 0.8)
    add_text(s, "ParkingOS records the business event when it happens.", 8.68, 4.3, 3.2, 0.62, size=17, color=TEAL_BRIGHT, bold=True)
    add_chip(s, "REVENUE ASSURANCE", 8.68, 5.28, 1.78, TEAL)
    add_footer(s, "ParkingOS · Business case · Evidence: PaymentService, PaymentStatus, TicketStatus, AnalyticsReportingView")

    # 5 — cost reduction
    s = prs.slides.add_slide(blank); set_bg(s); add_title(s, "04 / Cost reduction", "Replace repeated checking with live operational control", "The largest near-term savings opportunity is not a new fee. It is less rework per vehicle, shift, and garage.")
    rows = [("Occupancy checks", "Ask / walk / reconcile", "Live snapshot", BLUE), ("Ticket status", "Re-enter or search", "Durable lifecycle", ORANGE), ("Shift closeout", "Manually total", "Completed payments", TEAL), ("Management report", "Build spreadsheet", "Analytics + export", GREEN)]
    add_box(s, 0.8, 2.02, 8.0, 3.95, CARD, BORDER)
    add_text(s, "MANUAL COST CENTER", 1.1, 2.32, 2.3, 0.18, size=10, color=MUTED, bold=True, caps=True)
    add_text(s, "SYSTEM RESPONSE", 4.18, 2.32, 2.3, 0.18, size=10, color=TEAL, bold=True, caps=True)
    for i, (left, middle, right, accent) in enumerate(rows):
        y = 2.78 + i * 0.73
        add_text(s, left, 1.1, y, 2.2, 0.22, size=13, color=TEXT, bold=True)
        add_text(s, middle, 3.12, y, 1.55, 0.22, size=12, color=HEADER_MUTED)
        add_text(s, "→", 4.82, y-0.02, 0.25, 0.24, size=16, color=accent, bold=True, align=PP_ALIGN.CENTER)
        add_text(s, right, 5.25, y, 2.75, 0.22, size=13, color=accent, bold=True)
        add_line(s, 1.1, y+0.43, 8.42, y+0.43, BORDER, 0.6)
    add_box(s, 9.15, 2.02, 3.37, 3.95, SURFACE, BORDER)
    add_text(s, "OPERATING LEVER", 9.48, 2.32, 2.3, 0.18, size=10, color=YELLOW, bold=True, caps=True)
    add_text(s, "Fewer handoffs\nper vehicle", 9.48, 2.85, 2.4, 0.7, size=24, color=TEXT, bold=True)
    add_text(s, "Fewer handoffs\nper shift", 9.48, 4.02, 2.4, 0.7, size=24, color=TEXT, bold=True)
    add_text(s, "Fewer handoffs\nper garage", 9.48, 5.19, 2.4, 0.7, size=24, color=TEXT, bold=True)
    add_footer(s, "ParkingOS · Business case · Cost reduction levers are operational; savings require measured baseline data")

    # 6 — economics
    s = prs.slides.add_slide(blank); set_bg(s); add_title(s, "05 / Illustrative economics", "Make the value case measurable before scaling", "This is a scenario model—not a claimed result. Replace the assumptions with one garage’s baseline after launch.")
    add_box(s, 0.8, 2.0, 4.15, 4.5, CARD, BORDER)
    add_text(s, "MODEL INPUTS", 1.12, 2.3, 2.0, 0.18, size=10, color=TEAL, bold=True, caps=True)
    inputs = [("Vehicles / day", "180"), ("Average ticket", "$6.50"), ("Leakage protected", "2.0%"), ("Reconciliation hours / week", "12")]
    for i, (label, val) in enumerate(inputs):
        y = 2.82 + i * 0.72
        add_text(s, label, 1.12, y, 2.2, 0.2, size=12, color=HEADER_MUTED)
        add_text(s, val, 3.65, y-0.04, 0.85, 0.25, size=16, color=TEXT, bold=True, align=PP_ALIGN.RIGHT)
        add_line(s, 1.12, y+0.35, 4.55, y+0.35, BORDER, 0.6)
    add_text(s, "Replace these four inputs with measured operating data.", 1.12, 5.86, 3.3, 0.34, size=11, color=MUTED, italic=True)
    chart_data = CategoryChartData(); chart_data.categories = ["Conservative", "Base", "Upside"]
    chart_data.add_series("Annual value protected", (15100, 30200, 60400))
    chart = s.shapes.add_chart(XL_CHART_TYPE.COLUMN_CLUSTERED, Inches(5.35), Inches(2.0), Inches(7.2), Inches(3.35), chart_data).chart
    chart.has_title = True; chart.chart_title.text_frame.text = "Illustrative annual value protected · USD"
    chart.chart_title.text_frame.paragraphs[0].font.color.rgb = rgb(TEXT); chart.chart_title.text_frame.paragraphs[0].font.size = Pt(13)
    chart.value_axis.minimum_scale = 0; chart.value_axis.maximum_scale = 70000; chart.value_axis.major_unit = 20000
    chart.plots[0].has_data_labels = True; chart.plots[0].data_labels.position = XL_LABEL_POSITION.OUTSIDE_END
    chart.plots[0].data_labels.font.color.rgb = rgb(TEXT); chart.plots[0].data_labels.font.size = Pt(9)
    add_chart_style(chart, [TEAL], False)
    add_box(s, 5.35, 5.62, 7.2, 0.88, SURFACE, BORDER)
    add_rich_text(s, [("FORMULA  ", 9, True, YELLOW), ("vehicles × average ticket × days × leakage protected + avoided reconciliation labor", 13, True, TEXT)], 5.65, 5.92, 6.55, 0.28, valign=MSO_ANCHOR.MIDDLE)
    add_footer(s, "ParkingOS · Business case · Illustrative assumptions only; model should be calibrated with live garage data")

    # 7 — alternatives
    s = prs.slides.add_slide(blank); set_bg(s); add_title(s, "06 / Alternatives", "Better than manual because control is part of the workflow", "The differentiator is not “another payment screen.” It is the operating system around every parking event.")
    headers = ["Approach", "Live occupancy", "Ticket → payment", "Multi-garage", "Local control", "Reporting"]
    x0, y0 = 0.82, 2.12
    widths = [2.55, 1.75, 2.05, 1.7, 1.55, 1.55]
    x = x0
    for h, w in zip(headers, widths):
        add_cell(s, x, y0, w, 0.48, h, SURFACE, MUTED, 9, True)
        x += w + 0.03
    approaches = [
        ("Manual / paper", ["WEAK", "WEAK", "NONE", "HIGH", "LOW"], RED),
        ("Spreadsheet + POS", ["PARTIAL", "PARTIAL", "MANUAL", "MED", "MED"], YELLOW),
        ("Generic SaaS", ["VARIES", "STRONG", "STRONG", "LOW", "STRONG"], BLUE),
        ("ParkingOS", ["NATIVE", "NATIVE", "NATIVE", "HIGH", "NATIVE"], TEAL),
    ]
    for r, (name, vals, accent) in enumerate(approaches):
        y = y0 + 0.56 + r * 0.8
        add_cell(s, x0, y, widths[0], 0.62, name, CARD if r < 3 else HOVER, TEXT, 12, r == 3, PP_ALIGN.LEFT)
        x = x0 + widths[0] + 0.03
        for j, (val, w) in enumerate(zip(vals, widths[1:])):
            fill = (accent if r == 3 else CARD)
            color = BG if r == 3 else (accent if val in ("WEAK", "NONE", "LOW") else HEADER_MUTED)
            add_cell(s, x, y, w, 0.62, val, fill, color, 9, r == 3)
            x += w + 0.03
    add_box(s, 0.82, 6.0, 11.7, 0.55, SURFACE, BORDER)
    add_text(s, "ParkingOS wins when the buying decision is about operating control, not just transaction acceptance.", 1.08, 6.18, 11.1, 0.18, size=14, color=TEAL_BRIGHT, bold=True, align=PP_ALIGN.CENTER)
    add_footer(s, "ParkingOS · Business case · Alternative capabilities are directional and should be validated against shortlisted vendors")

    # 8 — defensibility
    s = prs.slides.add_slide(blank); set_bg(s); add_title(s, "07 / Defensibility", "The control layer is where the business value compounds", "A durable parking operation needs more than a front desk interface: it needs boundaries, history, and recovery.")
    cards = [
        ("PERSISTENCE", "Tickets, payments, garages, and reports survive the shift.", BLUE),
        ("OWNERSHIP", "Every ticket and payment stays tied to one garage.", ORANGE),
        ("AUTHORIZATION", "Actions respect user role and garage access.", PURPLE),
        ("RECOVERY", "Backup and migration paths protect operational history.", GREEN),
    ]
    for i, (title, body, accent) in enumerate(cards):
        x = 0.82 + (i % 2) * 6.02; y = 2.12 + (i // 2) * 1.6
        add_box(s, x, y, 5.48, 1.23, CARD, BORDER)
        add_box(s, x+0.28, y+0.28, 0.1, 0.67, accent, accent, radius=False)
        add_text(s, title, x+0.62, y+0.22, 2.8, 0.18, size=10, color=accent, bold=True, caps=True)
        add_text(s, body, x+0.62, y+0.54, 4.35, 0.42, size=14, color=TEXT, bold=True)
    add_box(s, 0.82, 5.58, 11.7, 0.8, SURFACE, BORDER)
    add_text(s, "This is what turns operational activity into an auditable business system.", 1.1, 5.83, 11.1, 0.26, size=18, color=TEXT, bold=True, align=PP_ALIGN.CENTER)
    add_footer(s, "ParkingOS · Business case · Evidence: PersistenceStore, GarageContext, authorization checks, backup/recovery documentation")

    # 9 — decision
    s = prs.slides.add_slide(blank); set_bg(s)
    add_title(s, "08 / Decision", "Measure one garage. Then scale what works.", "The next step is not a bigger feature list. It is a controlled operating baseline.")
    timeline = [("01", "BASELINE", "Capture vehicles/day, average ticket, reconciliation hours, error/refund rate.", BLUE), ("02", "PILOT", "Run one garage with live occupancy, ticket, payment, and report workflows.", TEAL), ("03", "COMPARE", "Replace the illustrative model with observed revenue and labor deltas.", YELLOW), ("04", "SCALE", "Expand through garage-scoped operations and shared reporting.", GREEN)]
    for i, (num, title, body, accent) in enumerate(timeline):
        x = 0.85 + i * 3.05
        add_box(s, x, 2.15, 2.5, 2.42, CARD, BORDER)
        add_text(s, num, x+0.25, 2.42, 0.45, 0.35, size=24, color=accent, bold=True)
        add_text(s, title, x+0.25, 3.1, 1.9, 0.2, size=11, color=accent, bold=True, caps=True)
        add_text(s, body, x+0.25, 3.52, 1.95, 0.62, size=13, color=TEXT, bold=True)
        if i < 3:
            add_line(s, x+2.5, 3.36, x+2.98, 3.36, TEAL, 2)
    add_box(s, 0.85, 5.28, 11.65, 0.95, TEAL, TEAL)
    add_text(s, "The opportunity is simple: fewer manual handoffs, better revenue visibility, and faster decisions per garage.", 1.2, 5.58, 10.95, 0.3, size=19, color=BG, bold=True, align=PP_ALIGN.CENTER)
    add_footer(s, "ParkingOS · Business case · Recommendation: instrument the pilot before making savings claims")

    # Speaker notes are not supported by python-pptx; source footers are included on every slide.
    prs.save(OUT)
    print(OUT)


if __name__ == "__main__":
    build()
