// Controlled native Wayland input target. Official API references:
// https://doc.qt.io/qt-6/qwidget.html
// https://doc.qt.io/qt-6/qkeyevent.html
// https://doc.qt.io/qt-6/qinputmethodevent.html
// https://doc.qt.io/qt-6/qplaintextedit.html
// This fixture observes its own widgets only; it neither captures global input
// nor identifies an event's transport, acknowledges RDP delivery, or measures latency.
#include <QApplication>
#include <QCloseEvent>
#include <QDateTime>
#include <QElapsedTimer>
#include <QFile>
#include <QFontDatabase>
#include <QGuiApplication>
#include <QInputMethodEvent>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QKeyEvent>
#include <QKeySequence>
#include <QLabel>
#include <QMap>
#include <QMouseEvent>
#include <QPainter>
#include <QPlainTextEdit>
#include <QPushButton>
#include <QResizeEvent>
#include <QSet>
#include <QTimer>
#include <QVBoxLayout>
#include <QWheelEvent>
#include <QWidget>
#include <array>
#include <charconv>
#include <cstdio>
#include <functional>
#include <stdexcept>
#include <string>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

namespace {
constexpr int MaxEvents = 4000;
constexpr int MaxTextUnits = 256;
constexpr std::size_t MaxMotionPoints = 64;
constexpr qint64 MaxLogBytes = 2 * 1024 * 1024;

QJsonObject rectangle(const QRect& value)
{
    return {{"x", value.x()}, {"y", value.y()}, {"width", value.width()}, {"height", value.height()}};
}

QJsonObject point(const QPointF& value)
{
    return {{"x", value.x()}, {"y", value.y()}};
}

class EventLog {
public:
    explicit EventLog(const QString& path)
    {
        const QByteArray name = QFile::encodeName(path);
        const int fd = ::open(name.constData(), O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0600);
        if (fd < 0) throw std::runtime_error("Cannot exclusively create the event log");
        if (::fchmod(fd, 0600) != 0 || !file.open(fd, QIODevice::WriteOnly, QFileDevice::AutoCloseHandle)) {
            ::close(fd);
            throw std::runtime_error("Cannot initialize private event log");
        }
        clock.start();
    }

    std::function<void(int)> requestExit;
    QSet<QString> heldKeys;
    int heldButtons = 0;
    int observedButtons = 0;

    bool record(const QString& name, QJsonObject fields = {})
    {
        if (finished) return false;
        // Reserve a completion record even when the event budget is exhausted.
        if (events >= MaxEvents - 1) { stop("event-limit", 1); return false; }
        counts[name]++;
        fields.insert("event", name);
        return write(fields, false);
    }

    void stop(const QString& reason, int code)
    {
        if (finished) return;
        finished = true;
        QJsonObject totals;
        for (auto item = counts.cbegin(); item != counts.cend(); ++item) totals.insert(item.key(), item.value());
        QJsonArray held;
        for (const QString& key : heldKeys) held.append(key);
        // Held state is never erased on focus loss: only actual release events
        // remove entries, so focus transitions cannot fabricate a release pass.
        QJsonObject fields{{"event", "completion"}, {"reason", reason}, {"eventCounts", totals},
                           {"heldKeys", held}, {"heldButtons", heldButtons},
                           {"lastObservedButtons", observedButtons}, {"exitCode", code}};
        if (!write(fields, true)) code = 1;
        if (requestExit) requestExit(code);
    }

    bool textFits(const QString& text)
    {
        if (text.size() <= MaxTextUnits) return true;
        stop("text-limit", 1);
        return false;
    }

private:
    bool write(QJsonObject fields, bool completion)
    {
        fields.insert("sequence", events);
        fields.insert("elapsedNanoseconds", QString::number(clock.nsecsElapsed()));
        const QByteArray line = QJsonDocument(fields).toJson(QJsonDocument::Compact) + '\n';
        // Leave 256 KiB for completion metadata (including every observed held key). All text-bearing fields are
        // independently bounded, and no unbounded widget/document dump is logged.
        const qint64 limit = completion ? MaxLogBytes : MaxLogBytes - 262144;
        if (bytes + line.size() > limit) {
            if (!completion) stop("byte-limit", 1);
            return false;
        }
        qint64 written = 0;
        while (written < line.size()) {
            const qint64 part = file.write(line.constData() + written, line.size() - written);
            if (part <= 0) { writeFailure(); return false; }
            written += part;
        }
        if (!file.flush()) { writeFailure(); return false; }
        bytes += line.size();
        events++;
        return true;
    }

    void writeFailure()
    {
        // An I/O error may prevent recording its own completion. Fail visibly
        // through process status; never report a successful completed log.
        finished = true;
        std::fputs("Event log write failed; stopping fixture\n", stderr);
        if (requestExit) requestExit(1);
    }

    QFile file;
    QElapsedTimer clock;
    QMap<QString, int> counts;
    qint64 bytes = 0;
    int events = 0;
    bool finished = false;
};

class Canvas final : public QWidget {
public:
    explicit Canvas(QWidget* parent) : QWidget(parent)
    {
        setFocusPolicy(Qt::StrongFocus);
        setMouseTracking(true);
        setMinimumSize(640, 280);
        setAccessibleName("Color grid and pointer event target");
    }
    int tick = 0;
protected:
    void paintEvent(QPaintEvent*) override
    {
        QPainter painter(this);
        painter.fillRect(rect(), QColor(24, 24, 24));
        const QColor colors[] = {Qt::red, Qt::green, Qt::blue, Qt::cyan, Qt::magenta, Qt::yellow,
                                 Qt::black, QColor(64, 64, 64), QColor(128, 128, 128),
                                 QColor(192, 192, 192), Qt::white};
        const int count = static_cast<int>(sizeof(colors) / sizeof(colors[0]));
        for (int i = 0; i < count; i++) {
            const int left = 8 + (width() - 16) * i / count;
            const int right = 8 + (width() - 16) * (i + 1) / count;
            painter.fillRect(QRect(left, 8, right - left, 64), colors[i]);
        }
        painter.setFont(QFontDatabase::systemFont(QFontDatabase::FixedFont));
        painter.setPen(QColor(90, 90, 90));
        for (int x = 0; x < width(); x += 100) painter.drawLine(x, 80, x, height() - 1);
        for (int y = 100; y < height(); y += 100) painter.drawLine(0, y, width() - 1, y);
        painter.setPen(Qt::white);
        for (int y = 100; y < height() - 20; y += 100)
            for (int x = 0; x < width() - 70; x += 100) painter.drawText(x + 4, y + 18, QString("%1,%2").arg(x).arg(y));
        painter.drawText(14, 92, QString("Cloudy RDP input target | tick %1 | canvas %2 x %3 logical px").arg(tick).arg(width()).arg(height()));
        // These mark the canvas boundary, not the captured desktop boundary.
        painter.setPen(QPen(Qt::white, 2));
        painter.drawRect(rect().adjusted(1, 1, -2, -2));
        painter.fillRect(2, 2, 6, 6, Qt::yellow);
        painter.fillRect(width() - 8, 2, 6, 6, Qt::cyan);
        painter.fillRect(2, height() - 8, 6, 6, Qt::magenta);
        painter.fillRect(width() - 8, height() - 8, 6, 6, Qt::green);
        // This is painted from received canvas events only. It is independent of
        // a client-side cursor prediction and makes no claim about event transport.
        if (retainedMotionPoints > 0) {
            painter.setPen(QPen(QColor(125, 213, 199), 2));
            const std::size_t first = (nextMotionPoint + MaxMotionPoints - retainedMotionPoints) % MaxMotionPoints;
            for (std::size_t i = 1; i < retainedMotionPoints; ++i)
                painter.drawLine(motionTrail[(first + i - 1) % MaxMotionPoints], motionTrail[(first + i) % MaxMotionPoints]);
            const QPointF last = motionTrail[(nextMotionPoint + MaxMotionPoints - 1) % MaxMotionPoints];
            for (const QPen& pen : {QPen(Qt::black, 5), QPen(Qt::white, 2)}) {
                painter.setPen(pen);
                painter.drawEllipse(last, 7, 7);
                painter.drawLine(last + QPointF(-11, 0), last + QPointF(11, 0));
                painter.drawLine(last + QPointF(0, -11), last + QPointF(0, 11));
            }
        }
        painter.setPen(Qt::white);
        painter.setBackground(QColor(16, 28, 34));
        painter.setBackgroundMode(Qt::OpaqueMode);
        painter.drawText(14, height() - 34, QString("Actual host canvas motion events: %1 (limit %2)").arg(motionEvents).arg(MaxEvents));
        const QString lastPosition = retainedMotionPoints == 0 ? "none" :
            QString("%1,%2").arg(motionTrail[(nextMotionPoint + MaxMotionPoints - 1) % MaxMotionPoints].x(), 0, 'f', 1)
                              .arg(motionTrail[(nextMotionPoint + MaxMotionPoints - 1) % MaxMotionPoints].y(), 0, 'f', 1);
        painter.drawText(14, height() - 14, QString("Last host position: %1 | trail <= 64 | no transport attribution").arg(lastPosition));
    }
    void mousePressEvent(QMouseEvent* event) override { setFocus(Qt::MouseFocusReason); event->accept(); }
    void mouseReleaseEvent(QMouseEvent* event) override { event->accept(); }
    void mouseMoveEvent(QMouseEvent* event) override
    {
        // The existing event filter logs the same event under the finite budget.
        // Saturation is defensive; no timer, pointer query or synthetic event adds a point.
        if (motionEvents < MaxEvents) {
            motionEvents++;
            motionTrail[nextMotionPoint] = event->position();
            nextMotionPoint = (nextMotionPoint + 1) % MaxMotionPoints;
            if (retainedMotionPoints < MaxMotionPoints) retainedMotionPoints++;
            update();
        }
        event->accept();
    }
    void wheelEvent(QWheelEvent* event) override { event->accept(); }
    void keyPressEvent(QKeyEvent* event) override { event->accept(); }
    void keyReleaseEvent(QKeyEvent* event) override { event->accept(); }
private:
    std::array<QPointF, MaxMotionPoints> motionTrail{};
    std::size_t nextMotionPoint = 0;
    std::size_t retainedMotionPoints = 0;
    int motionEvents = 0;
};

class Editor final : public QPlainTextEdit {
public:
    explicit Editor(QWidget* parent) : QPlainTextEdit(parent)
    {
        // Selection transfer is outside this target: prevent implicit primary-selection
        // publication as well as explicit clipboard shortcuts/context actions.
        setTextInteractionFlags(Qt::TextEditable);
        setContextMenuPolicy(Qt::NoContextMenu);
        setAcceptDrops(false);
        viewport()->setAcceptDrops(false);
        setMaximumHeight(150);
        setAccessibleName("Controlled text input, maximum 256 UTF-16 units");
        setPlaceholderText("Empty native editor: controlled test text only; no credentials or clipboard operations");
    }
protected:
    void keyPressEvent(QKeyEvent* event) override
    {
        // Keep editing native while excluding clipboard reads/writes from this fixture.
        if (event->matches(QKeySequence::Copy) || event->matches(QKeySequence::Cut) || event->matches(QKeySequence::Paste)) {
            event->accept();
            return;
        }
        QPlainTextEdit::keyPressEvent(event);
    }
};

class Target final : public QWidget {
public:
    Target(EventLog& output, int seconds) : log(output)
    {
        setWindowTitle("Cloudy RDP input target — controlled window only");
        auto* layout = new QVBoxLayout(this);
        layout->addWidget(new QLabel("Cloudy RDP input target", this));
        auto* instructions = new QLabel("Own-window events only. No credentials. Click the canvas for keys/drag/wheel; use the empty editor for text.\n"
                                       "Corner marks bound this canvas, not the desktop. Focus loss does not itself prove remote key/button release.", this);
        instructions->setWordWrap(true);
        layout->addWidget(instructions);
        auto* quit = new QPushButton(QString("Close target (automatic stop after %1 seconds)").arg(seconds), this);
        layout->addWidget(quit);
        editor = new Editor(this);
        layout->addWidget(editor);
        canvas = new Canvas(this);
        layout->addWidget(canvas, 1);
        for (QObject* object : {static_cast<QObject*>(canvas), static_cast<QObject*>(editor), static_cast<QObject*>(editor->viewport())})
            object->installEventFilter(this);
        QObject::connect(quit, &QPushButton::clicked, this, &QWidget::close);
        QObject::connect(editor, &QPlainTextEdit::textChanged, this, [this] {
            const QString text = editor->toPlainText();
            if (log.textFits(text)) log.record("text-change", {{"text", text}, {"utf16Units", text.size()}});
        });
        timer.setInterval(500);
        QObject::connect(&timer, &QTimer::timeout, this, [this] { canvas->tick++; canvas->update(); });
        timer.start();
        QTimer::singleShot(seconds * 1000, this, [this] { log.stop("deadline", 0); });
        resize(1100, 760);
    }

    void geometryRecord(const QString& reason)
    {
        log.record("geometry", {{"reason", reason}, {"window", rectangle(geometry())},
                                {"windowFrame", rectangle(frameGeometry())}, {"devicePixelRatio", devicePixelRatioF()},
                                {"canvas", rectangle(canvas->geometry())}, {"editor", rectangle(editor->geometry())},
                                {"editorViewport", rectangle(editor->viewport()->geometry())}});
    }

protected:
    bool eventFilter(QObject* watched, QEvent* event) override
    {
        const QString recipient = watched == canvas ? "canvas" : watched == editor ? "editor" : "editor-viewport";
        QJsonObject fields{{"recipient", recipient}};
        switch (event->type()) {
        case QEvent::KeyPress:
        case QEvent::KeyRelease: {
            auto* key = static_cast<QKeyEvent*>(event);
            if (!log.textFits(key->text())) return true;
            const QString identity = QString("%1:%2").arg(key->key()).arg(key->nativeScanCode());
            if (!key->isAutoRepeat()) {
                if (event->type() == QEvent::KeyPress) log.heldKeys.insert(identity);
                else log.heldKeys.remove(identity);
            }
            fields.insert("key", key->key());
            fields.insert("nativeScanCode", static_cast<qint64>(key->nativeScanCode()));
            fields.insert("nativeVirtualKey", static_cast<qint64>(key->nativeVirtualKey()));
            fields.insert("nativeModifiers", static_cast<qint64>(key->nativeModifiers()));
            fields.insert("modifiers", static_cast<int>(key->modifiers()));
            fields.insert("autoRepeat", key->isAutoRepeat());
            fields.insert("text", key->text());
            log.record(event->type() == QEvent::KeyPress ? "key-press" : "key-release", fields);
            break;
        }
        case QEvent::InputMethod: {
            auto* method = static_cast<QInputMethodEvent*>(event);
            if (!log.textFits(method->commitString()) || !log.textFits(method->preeditString()) ||
                method->commitString().size() + method->preeditString().size() > MaxTextUnits) {
                log.stop("input-method-text-limit", 1);
                return true;
            }
            fields.insert("commit", method->commitString());
            fields.insert("preedit", method->preeditString());
            fields.insert("replacementStart", method->replacementStart());
            fields.insert("replacementLength", method->replacementLength());
            log.record("input-method", fields);
            break;
        }
        case QEvent::MouseButtonPress:
        case QEvent::MouseButtonRelease:
        case QEvent::MouseButtonDblClick:
        case QEvent::MouseMove: {
            auto* mouse = static_cast<QMouseEvent*>(event);
            fields.insert("position", point(mouse->position()));
            fields.insert("button", static_cast<int>(mouse->button()));
            fields.insert("buttons", static_cast<int>(mouse->buttons()));
            fields.insert("modifiers", static_cast<int>(mouse->modifiers()));
            log.observedButtons = static_cast<int>(mouse->buttons());
            if (event->type() == QEvent::MouseButtonPress || event->type() == QEvent::MouseButtonDblClick)
                log.heldButtons |= static_cast<int>(mouse->button());
            else if (event->type() == QEvent::MouseButtonRelease)
                log.heldButtons &= ~static_cast<int>(mouse->button());
            const QString name = event->type() == QEvent::MouseMove ? "mouse-move" :
                                 event->type() == QEvent::MouseButtonRelease ? "mouse-release" :
                                 event->type() == QEvent::MouseButtonDblClick ? "mouse-double-click" : "mouse-press";
            log.record(name, fields);
            // Middle-button paste is excluded from the editor, including its viewport.
            if (watched != canvas && mouse->button() == Qt::MiddleButton) return true;
            break;
        }
        case QEvent::Wheel: {
            auto* wheel = static_cast<QWheelEvent*>(event);
            fields.insert("position", point(wheel->position()));
            fields.insert("angleDelta", point(wheel->angleDelta()));
            fields.insert("pixelDelta", point(wheel->pixelDelta()));
            fields.insert("buttons", static_cast<int>(wheel->buttons()));
            fields.insert("modifiers", static_cast<int>(wheel->modifiers()));
            log.record("wheel", fields);
            break;
        }
        case QEvent::FocusIn:
        case QEvent::FocusOut: {
            auto* focus = static_cast<QFocusEvent*>(event);
            fields.insert("reason", static_cast<int>(focus->reason()));
            fields.insert("observedHeldKeyCount", log.heldKeys.size());
            fields.insert("heldButtons", log.heldButtons);
            fields.insert("lastObservedButtons", log.observedButtons);
            log.record(event->type() == QEvent::FocusIn ? "focus-in" : "focus-out", fields);
            break;
        }
        default: break;
        }
        return QWidget::eventFilter(watched, event);
    }

    bool event(QEvent* value) override
    {
        if (value->type() == QEvent::WindowActivate || value->type() == QEvent::WindowDeactivate)
            log.record(value->type() == QEvent::WindowActivate ? "window-active" : "window-inactive");
        return QWidget::event(value);
    }
    void resizeEvent(QResizeEvent* value) override
    {
        QWidget::resizeEvent(value);
        if (canvas && editor) geometryRecord("resize");
    }
    void closeEvent(QCloseEvent* value) override { log.stop("operator-close", 0); value->accept(); }

private:
    EventLog& log;
    Canvas* canvas = nullptr;
    Editor* editor = nullptr;
    QTimer timer;
};

void usage()
{
    std::puts("Usage: rdp_input_target --log NEW_PRIVATE_JSONL [--seconds 60..1200]\n"
              "Native Wayland window; default lifespan 600 seconds. Existing log paths are rejected.\n"
              "No global input capture, networking, clipboard operations or RDP pass/latency claims.\n"
              "--help performs no GUI initialization.");
}
} // namespace

int main(int argc, char** argv)
{
    QString path;
    int seconds = 600;
    for (int i = 1; i < argc; i++) {
        const std::string option = argv[i];
        if (option == "--help" && argc == 2) { usage(); return 0; }
        if ((option == "--log" || option == "--seconds") && i + 1 < argc) {
            const std::string value = argv[++i];
            if (option == "--log") path = QString::fromLocal8Bit(value.c_str());
            else {
                const auto result = std::from_chars(value.data(), value.data() + value.size(), seconds);
                if (result.ec != std::errc() || result.ptr != value.data() + value.size()) {
                    std::fputs("Invalid seconds value\n", stderr); return 2;
                }
            }
        } else { usage(); return 2; }
    }
    if (path.isEmpty() || seconds < 60 || seconds > 1200) { usage(); return 2; }
    try {
        EventLog log(path);
        qputenv("QT_QPA_PLATFORM", "wayland");
        int qtArgc = 1;
        char* qtArgv[] = {argv[0], nullptr};
        QApplication app(qtArgc, qtArgv);
        if (!QGuiApplication::platformName().startsWith("wayland")) {
            std::fputs("Native Wayland backend required\n", stderr); return 1;
        }
        log.requestExit = [&app](int code) { QTimer::singleShot(0, &app, [&app, code] { app.exit(code); }); };
        log.record("provenance", {{"schemaVersion", 1}, {"fixtureRevision", "host-motion-1"},
                                  {"maximumMotionTrailPoints", static_cast<int>(MaxMotionPoints)}, {"qtRuntime", qVersion()}, {"qtCompile", QT_VERSION_STR},
                                  {"platform", QGuiApplication::platformName()}, {"pid", QCoreApplication::applicationPid()},
                                  {"startedUtc", QDateTime::currentDateTimeUtc().toString(Qt::ISODateWithMs)},
                                  {"lifetimeSeconds", seconds}, {"maximumEvents", MaxEvents},
                                  {"maximumLogBytes", MaxLogBytes}, {"maximumTextUtf16Units", MaxTextUnits},
                                  {"scope", "own-window input and painted target; no transport or latency assertion"}});
        Target target(log, seconds);
        target.showMaximized();
        QTimer::singleShot(0, &target, [&target] { target.geometryRecord("shown"); });
        const int result = app.exec();
        log.stop("event-loop-exit", result);
        return result;
    } catch (const std::exception& error) {
        std::fprintf(stderr, "Input target failed: %s\n", error.what());
        return 1;
    }
}
