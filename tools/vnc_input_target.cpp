// Own-window X11 qualification fixture; source-guided by rdp_input_target.cpp.
// Qt public contracts: https://doc.qt.io/qt-6/qwidget.html
// https://doc.qt.io/qt-6/qmouseevent.html https://doc.qt.io/qt-6/qkeyevent.html
// No global capture, clipboard, text serialization or transport attribution.
#include <QApplication>
#include <QCloseEvent>
#include <QElapsedTimer>
#include <QFile>
#include <QFocusEvent>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QKeyEvent>
#include <QMouseEvent>
#include <QPainter>
#include <QSet>
#include <QScreen>
#include <QWindow>
#include <QTimer>
#include <QWheelEvent>
#include <QWidget>
#include <array>
#include <charconv>
#include <cstdio>
#include <stdexcept>
#include <string>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

namespace {
constexpr int MaxEvents = 4000;
constexpr qint64 MaxBytes = 2 * 1024 * 1024;
constexpr std::size_t TrailSize = 64;
class Canvas final : public QWidget {
public:
    Canvas(int directory, int seconds)
    {
        // openat binds output to the validated directory descriptor, even if its
        // pathname is renamed. Never truncate an existing file or follow a link.
        const int fd = ::openat(directory, "events.jsonl", O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0600);
        if (fd < 0) throw std::runtime_error("Cannot exclusively create journal");
        if (!file.open(fd, QIODevice::WriteOnly, QFileDevice::AutoCloseHandle)) {
            ::close(fd); throw std::runtime_error("Cannot open journal descriptor");
        }
        directoryFd = ::fcntl(directory, F_DUPFD_CLOEXEC, 0);
        if (directoryFd < 0) throw std::runtime_error("Cannot retain output directory");
        clock.start();
        resize(1280, 720); setMinimumSize(640, 360);
        setWindowTitle("Cloudy controlled VNC X11 canvas — xcb-input-1");
        setAccessibleName("Controlled numeric input and paint qualification canvas");
        setFocusPolicy(Qt::StrongFocus); setMouseTracking(true);
        record({{"event", "start"}, {"revision", "xcb-input-1"}, {"qt", qVersion()},
                {"platform", QGuiApplication::platformName()}, {"seconds", seconds},
                {"maximumEvents", MaxEvents}, {"maximumBytes", MaxBytes}});
        QObject::connect(&paintTimer, &QTimer::timeout, this, [this] {
            struct stat stop{};
            // This fixed marker is metadata-only; never follow a symlink or read
            // file content. The retained descriptor pins the validated directory.
            if (::fstatat(directoryFd, "stop", &stop, AT_SYMLINK_NOFOLLOW) == 0 &&
                S_ISREG(stop.st_mode) && stop.st_uid == ::getuid() && (stop.st_mode & 0077) == 0) {
                finish("operator-stop", 0); return;
            }
            ++ticks; update();
        });
        paintTimer.start(250);
        QTimer::singleShot(seconds * 1000, this, [this] { finish("time-limit", 0); });
    }
    ~Canvas() override { if (directoryFd >= 0) ::close(directoryFd); }
    void recordPresentation(QScreen* selected)
    {
        record({{"event", "presentation"}, {"platform", QGuiApplication::platformName()},
                {"requestedScreenMatched", selected!=nullptr},
                {"windowScreenMatchesRequest", selected && windowHandle() && windowHandle()->screen()==selected}, {"fullscreen", isFullScreen()}});
    }
    void finish(const char* reason, int code)
    {
        if (finished) return;
        finished = true; paintTimer.stop();
        QJsonArray held;
        for (int key : keys) held.append(key);
        QJsonObject item{{"event", "completion"}, {"reason", reason}, {"exitCode", code},
            {"motionEvents", motions}, {"buttonEvents", buttons}, {"keyEvents", keyEvents},
            {"focusTransitions", focusTransitions}, {"activationTransitions", activationTransitions},
            {"hasFocus", hasFocus()}, {"activeWindow", isActiveWindow()}, {"wheelEvents", wheels}, {"timerTicks", ticks}, {"paintEvents", paints},
            {"heldKeys", held}, {"lastObservedButtons", observedButtons}};
        if (!write(item)) code = 1;
        QTimer::singleShot(0, qApp, [code] { QApplication::exit(code); });
    }
protected:
    void paintEvent(QPaintEvent*) override
    {
        ++paints;
        QPainter p(this); p.fillRect(rect(), QColor(24, 24, 24));
        const QColor colors[] = {Qt::red, Qt::green, Qt::blue, Qt::cyan, Qt::magenta, Qt::yellow, Qt::white};
        for (int i = 0; i < 7; ++i) p.fillRect(i * width() / 7, 0, width() / 7 + 1, 65, colors[i]);
        p.fillRect(12, 78, 45, 45, (ticks % 2) ? Qt::yellow : Qt::blue);
        p.setPen(QColor(80, 80, 80));
        for (int x = 0; x < width(); x += 100) p.drawLine(x, 130, x, height());
        for (int y = 130; y < height(); y += 100) p.drawLine(0, y, width(), y);
        p.setPen(Qt::white);
        p.drawText(70, 95, QString("Timer tick %1 | actual paint callbacks %2 (not input)").arg(ticks).arg(paints));
        p.drawText(14, 150, QString("Actual own-window input: moves %1 | buttons %2 | keys %3 | wheel %4")
                   .arg(motions).arg(buttons).arg(keyEvents).arg(wheels));
        p.drawText(14, 175, "Controlled marker keys only; no free-text logging. Trail <=64. No transport or latency assertion.");
        p.setPen(QPen(QColor(125, 213, 199), 2));
        const std::size_t first = (next + TrailSize - retained) % TrailSize;
        for (std::size_t i = 1; i < retained; ++i) p.drawLine(trail[(first+i-1)%TrailSize], trail[(first+i)%TrailSize]);
        if (retained) {
            const QPointF last = trail[(next+TrailSize-1)%TrailSize];
            p.setPen(QPen(Qt::white, 3)); p.drawEllipse(last, 7, 7);
            p.drawLine(last+QPointF(-12,0), last+QPointF(12,0));
            p.drawLine(last+QPointF(0,-12), last+QPointF(0,12));
        }
        p.setPen(Qt::white); p.drawRect(rect().adjusted(1,1,-2,-2));
    }
    void mouseMoveEvent(QMouseEvent* e) override
    {
        if (finished) return;
        ++motions; trail[next] = e->position(); next = (next+1)%TrailSize;
        if (retained < TrailSize) ++retained;
        mouse("motion", e); update();
    }
    void mousePressEvent(QMouseEvent* e) override { if (!finished) { ++buttons; mouse("button-down", e); update(); } }
    void mouseReleaseEvent(QMouseEvent* e) override { if (!finished) { ++buttons; mouse("button-up", e); update(); } }
    void keyPressEvent(QKeyEvent* e) override { key(true, e); }
    void keyReleaseEvent(QKeyEvent* e) override { key(false, e); }
    void wheelEvent(QWheelEvent* e) override
    {
        if (finished) return;
        ++wheels; record({{"event", "wheel"}, {"dx", e->angleDelta().x()}, {"dy", e->angleDelta().y()}}); update();
    }
    void focusInEvent(QFocusEvent* e) override { ++focusTransitions; focusRecord("focus-in", static_cast<int>(e->reason())); QWidget::focusInEvent(e); }
    void focusOutEvent(QFocusEvent* e) override { ++focusTransitions; focusRecord("focus-out", static_cast<int>(e->reason())); QWidget::focusOutEvent(e); }
    bool event(QEvent* e) override
    {
        if (e->type()==QEvent::WindowActivate || e->type()==QEvent::WindowDeactivate) {
            ++activationTransitions; focusRecord(e->type()==QEvent::WindowActivate ? "window-active" : "window-inactive", 0);
        }
        return QWidget::event(e);
    }
    void closeEvent(QCloseEvent* e) override { finish("operator-close", 0); e->accept(); }
private:
    void focusRecord(const char* name, int reason)
    {
        record({{"event", name}, {"reasonCode", reason}, {"hasFocus", hasFocus()}, {"activeWindow", isActiveWindow()}});
    }
    void mouse(const char* name, QMouseEvent* e)
    {
        observedButtons = static_cast<int>(e->buttons());
        record({{"event", name}, {"x", e->position().x()}, {"y", e->position().y()},
                {"button", static_cast<int>(e->button())}, {"buttons", observedButtons}});
        e->accept();
    }
    void key(bool down, QKeyEvent* e)
    {
        if (finished) return;
        ++keyEvents;
        // Only marker/control codes are retained, never text(), native scan code,
        // arbitrary printable codes, clipboard data or an editor/document dump.
        const int k = e->key();
        const bool marker = k == Qt::Key_F8 || k == Qt::Key_F9 || k == Qt::Key_Shift || k == Qt::Key_Control || k == Qt::Key_Alt;
        if (marker && !e->isAutoRepeat()) { if (down) keys.insert(k); else keys.remove(k); }
        record({{"event", down ? "key-down" : "key-up"}, {"markerCode", marker ? k : 0}, {"repeat", e->isAutoRepeat()}, {"hasFocus", hasFocus()}, {"activeWindow", isActiveWindow()}});
        e->accept(); update();
    }
    void record(QJsonObject item)
    {
        if (finished) return;
        if (events >= MaxEvents-1 || bytes >= MaxBytes-65536) { finish("journal-limit", 1); return; }
        if (!write(item)) { finished = true; paintTimer.stop(); QTimer::singleShot(0, qApp, [] { QApplication::exit(1); }); }
    }
    bool write(QJsonObject item)
    {
        item.insert("sequence", events); item.insert("elapsedNanoseconds", QString::number(clock.nsecsElapsed()));
        const QByteArray line = QJsonDocument(item).toJson(QJsonDocument::Compact)+'\n';
        if (bytes+line.size() > MaxBytes) return false;
        qint64 offset = 0;
        while (offset < line.size()) { const qint64 n = file.write(line.constData()+offset, line.size()-offset); if (n <= 0) return false; offset += n; }
        if (!file.flush()) return false;
        bytes += line.size(); ++events; return true;
    }
    QFile file; QElapsedTimer clock; QTimer paintTimer; QSet<int> keys;
    std::array<QPointF, TrailSize> trail{};
    std::size_t next = 0, retained = 0;
    qint64 bytes = 0;
    int events = 0, motions = 0, buttons = 0, keyEvents = 0, wheels = 0, ticks = 0, paints = 0, observedButtons = 0;
    int focusTransitions = 0, activationTransitions = 0;
    int directoryFd = -1;
    bool finished = false;
};
}
int main(int argc, char** argv)
{
    std::string directory, platform="xcb", screen; int seconds = 600; bool durationSeen = false, platformSeen = false, screenSeen = false;
    for (int i=1; i<argc; ++i) {
        const std::string option = argv[i];
        if ((option == "--output-dir" || option == "--seconds" || option == "--platform" || option == "--screen") && i+1 < argc) {
            const std::string value = argv[++i];
            if (option == "--output-dir" && directory.empty()) directory = value;
            else if (option == "--platform" && !platformSeen) { platformSeen=true; platform=value; }
            else if (option == "--screen" && !screenSeen) { screenSeen=true; screen=value; }
            else if (option == "--seconds" && !durationSeen) {
                durationSeen = true;
                const auto result = std::from_chars(value.data(), value.data()+value.size(), seconds);
                if (result.ec != std::errc() || result.ptr != value.data()+value.size()) return 2;
            } else return 2;
        } else return 2;
    }
    if (directory.empty() || directory.front() != '/' || seconds < 1 || seconds > 600) return 2;
    if ((platform!="xcb" && platform!="wayland") || (platform=="xcb" && screenSeen) || (platform=="wayland" && (!screenSeen || screen.empty()))) return 2;
    int fd = ::open(directory.c_str(), O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    struct stat st{};
    if (fd < 0) return 2;
    if (::fstat(fd, &st) != 0 || st.st_uid != ::getuid() || (st.st_mode & 0077) != 0) { ::close(fd); return 2; }
    try {
        qputenv("QT_QPA_PLATFORM", QByteArray::fromStdString(platform));
        int qtArgc = 1; char* qtArgv[] = {argv[0], nullptr};
        QApplication app(qtArgc, qtArgv);
        if (QGuiApplication::platformName() != QString::fromStdString(platform)) { ::close(fd); return 1; }
        QScreen* selected=nullptr;
        if (platform=="wayland") {
            for (QScreen* candidate:QGuiApplication::screens()) if (candidate->name()==QString::fromLocal8Bit(screen.c_str())) {
                if (selected) { ::close(fd); return 2; }
                selected=candidate;
            }
            if (!selected) { ::close(fd); return 2; }
        }
        Canvas canvas(fd, seconds); ::close(fd); fd = -1;
        if (selected) {
            canvas.winId(); canvas.windowHandle()->setScreen(selected);
            if (canvas.windowHandle()->screen()!=selected) { canvas.finish("screen-assignment-failed",1); return 1; }
            QObject::connect(&app,&QGuiApplication::screenRemoved,&canvas,[&canvas,selected](QScreen* removed) {
                if (removed==selected) canvas.finish("selected-output-lost",1);
            });
            canvas.showFullScreen();
        }
        else canvas.show();
        QTimer::singleShot(0,&canvas,[&canvas,selected] { canvas.recordPresentation(selected); });
        const int result = app.exec(); canvas.finish("event-loop-exit", result); return result;
    } catch (const std::exception& error) {
        if (fd >= 0) ::close(fd);
        std::fprintf(stderr, "Canvas failed: %s\n", error.what()); return 1;
    }
}
