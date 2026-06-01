//! Chainlink desktop shell (UC-052).
//!
//! Spawns the Quarkus backend as a child process bound to a random loopback port, waits for it to
//! become reachable, then points the webview at it. The backend is launched with the `desktop`
//! Quarkus profile (serves the SPA at `/`, REST under `/api`, OIDC compiled out) and the three
//! install-specific runtime paths (`CHAINLINK_DB_PATH`, `CHAINLINK_FAVICON_CACHE_DIR`,
//! `CHAINLINK_DESKTOP_WEB_ROOT`). The child is killed when the app exits.

use std::net::TcpListener;
use std::process::{Child, Command};
use std::sync::Mutex;
use std::time::{Duration, Instant};

use tauri::{Manager, RunEvent};

/// Holds the backend child process so it can be killed on exit.
struct Backend(Mutex<Option<Child>>);

/// How long to wait for the backend to answer the readiness probe before giving up.
const STARTUP_TIMEOUT: Duration = Duration::from_secs(90);

/// Grabs a free loopback port by binding to port 0 and reading back the assigned port. There is a
/// small TOCTOU window before the JVM binds it, which is acceptable for a single-user desktop app.
fn pick_free_port() -> std::io::Result<u16> {
    let listener = TcpListener::bind("127.0.0.1:0")?;
    Ok(listener.local_addr()?.port())
}

/// Polls the backend's PermitAll `/api/ping` endpoint (204) until it answers or the timeout elapses.
fn wait_until_ready(port: u16) -> bool {
    let url = format!("http://127.0.0.1:{port}/api/ping");
    let deadline = Instant::now() + STARTUP_TIMEOUT;
    while Instant::now() < deadline {
        match ureq::get(&url).timeout(Duration::from_secs(2)).call() {
            Ok(resp) if (200..300).contains(&resp.status()) => return true,
            _ => std::thread::sleep(Duration::from_millis(300)),
        }
    }
    false
}

fn spawn_backend(app: &tauri::App, port: u16) -> Result<Child, Box<dyn std::error::Error>> {
    let resource_dir = app.path().resource_dir()?;
    let data_dir = app.path().app_data_dir()?;
    std::fs::create_dir_all(&data_dir)?;

    let jar = resource_dir.join("quarkus-app").join("quarkus-run.jar");
    let web_root = resource_dir.join("web");

    // Prefer the bundled Java runtime so the app doesn't depend on whatever `java` (if any) is on
    // the system: a dependency requires Java 25, and a Finder/launchd launch often resolves an
    // older system JVM. Fall back to `java` on PATH only if the runtime wasn't bundled.
    let bundled_java = resource_dir.join("runtime").join("bin").join("java");
    let java = if bundled_java.is_file() {
        bundled_java.into_os_string()
    } else {
        "java".into()
    };

    // Pipe the JVM's stdout/stderr to a log file in the data dir so backend startup failures are
    // diagnosable (a GUI launch has no console).
    let log_path = data_dir.join("backend.log");
    let log = std::fs::File::create(&log_path)?;
    let log_err = log.try_clone()?;
    eprintln!("Chainlink backend log: {}", log_path.display());

    let child = Command::new(&java)
        .arg("-jar")
        .arg(&jar)
        .env("QUARKUS_PROFILE", "desktop")
        .env("QUARKUS_HTTP_HOST", "127.0.0.1")
        .env("QUARKUS_HTTP_PORT", port.to_string())
        .env("CHAINLINK_DB_PATH", data_dir.join("chainlink.db"))
        .env("CHAINLINK_FAVICON_CACHE_DIR", data_dir.join("favicon-cache"))
        .env("CHAINLINK_DESKTOP_WEB_ROOT", &web_root)
        .stdout(std::process::Stdio::from(log))
        .stderr(std::process::Stdio::from(log_err))
        .spawn()?;
    Ok(child)
}

/// Stops the backend gracefully: SIGTERM lets Quarkus shut down cleanly (flush/close SQLite + WAL),
/// then SIGKILL as a fallback if it hasn't exited within a few seconds.
fn shutdown_backend(child: &mut Child) {
    #[cfg(unix)]
    // SAFETY: sending a signal to our own child's pid is sound; the result is intentionally ignored.
    unsafe {
        libc::kill(child.id() as libc::pid_t, libc::SIGTERM);
    }
    #[cfg(not(unix))]
    let _ = child.kill();

    let deadline = Instant::now() + Duration::from_secs(5);
    while Instant::now() < deadline {
        match child.try_wait() {
            Ok(Some(_)) => return, // exited cleanly
            Ok(None) => std::thread::sleep(Duration::from_millis(100)),
            Err(_) => break,
        }
    }
    let _ = child.kill(); // force-kill fallback
    let _ = child.wait();
}

pub fn run() {
    tauri::Builder::default()
        // Must be the first plugin: a second launch hands its argv to this instance instead of
        // starting a duplicate app + backend; we just focus the existing window.
        .plugin(tauri_plugin_single_instance::init(|app, _argv, _cwd| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.unminimize();
                let _ = window.show();
                let _ = window.set_focus();
            }
        }))
        .manage(Backend(Mutex::new(None)))
        .setup(|app| {
            let port = pick_free_port()?;

            let child = spawn_backend(app, port).map_err(|e| {
                format!("failed to start the Chainlink backend (is Java installed?): {e}")
            })?;
            app.state::<Backend>().0.lock().unwrap().replace(child);

            // The window starts on the bundled splash page (frontendDist). Wait for the backend in
            // a background thread, then navigate the same window to it.
            let handle = app.handle().clone();
            std::thread::spawn(move || {
                let ready = wait_until_ready(port);
                let Some(window) = handle.get_webview_window("main") else {
                    return;
                };
                if ready {
                    if let Ok(url) = format!("http://127.0.0.1:{port}/").parse() {
                        let _ = window.navigate(url);
                    }
                } else {
                    eprintln!("Chainlink backend did not become ready within {STARTUP_TIMEOUT:?}");
                }
            });

            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("error while building the Chainlink desktop app")
        .run(|app_handle, event| {
            // Kill the backend when the app is shutting down so no orphan JVM is left behind.
            if let RunEvent::Exit = event {
                if let Some(state) = app_handle.try_state::<Backend>() {
                    if let Some(mut child) = state.0.lock().unwrap().take() {
                        shutdown_backend(&mut child);
                    }
                }
            }
        });
}
