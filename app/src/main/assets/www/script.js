/* =========================================================
   SCRIPT.JS - Logika Utama, Fitur Anti-Malware & Engine Scanner
   ========================================================= */

document.addEventListener('DOMContentLoaded', function () {
    // ---- DOM Elements ----
    const btnStart = document.getElementById('btn-start');
    const btnStop = document.getElementById('btn-stop');
    const btnClearHistory = document.getElementById('btn-clear-history');
    const btnCopyData = document.getElementById('btn-copy-data');
    const btnVisitUrl = document.getElementById('btn-visit-url');
    const fileUploadInput = document.getElementById('upload-file');
    
    const scannerStandbyOverlay = document.getElementById('scanner-standby-overlay');
    const cameraStatusText = document.getElementById('camera-status');
    const selectedCameraInfo = document.getElementById('selected-camera-info');
    
    const malwareAlert = document.getElementById('malware-alert');
    const detectedMaliciousUrl = document.getElementById('detected-malicious-url');
    
    const resultContainer = document.getElementById('result-container');
    const threatIndicator = document.getElementById('threat-indicator');
    const scannedRawText = document.getElementById('scanned-raw-text');
    const previewSection = document.getElementById('preview-section');
    const previewIframe = document.getElementById('preview-iframe');
    
    const historyList = document.getElementById('history-list');
    const historyEmpty = document.getElementById('history-empty');
    
    const countTotalScans = document.getElementById('total-scans');
    const countBlockedThreats = document.getElementById('blocked-threats');

    // ---- Application State ----
    let html5Qrcode = null;
    let cameraActive = false;
    let scanHistory = [];
    
    // IOC Malware Extensions
    const dangerousExtensions = ['.exe', '.apk', '.bat', '.msi', '.vbs', '.scr', '.cmd', '.pif', '.reg', '.sh'];

    // Load History & Stats on startup
    initApp();

    function initApp() {
        // Load history from localStorage
        const savedHistory = localStorage.getItem('qr_guard_history');
        if (savedHistory) {
            try {
                scanHistory = JSON.parse(savedHistory);
            } catch (e) {
                scanHistory = [];
            }
        }
        
        // Initialize HTML5 QR Reader instance (with ID "reader")
        html5Qrcode = new Html5Qrcode("reader");

        // Render logs UI
        renderHistory();
        updateStats();

        // Register Action Listeners
        btnStart.addEventListener('click', startScanning);
        btnStop.addEventListener('click', stopScanning);
        btnClearHistory.addEventListener('click', clearAllHistory);
        btnCopyData.addEventListener('click', copyScannedText);
        fileUploadInput.addEventListener('change', scanFromGallery);
    }

    // ---- Start Camera scanning ----
    async function startScanning() {
        if (cameraActive) return;

        // Hide warning and results on start new scan
        hideStatusBanners();

        try {
            cameraStatusText.textContent = "Scanning...";
            cameraStatusText.className = "text-xl font-bold font-mono text-purple-400 mt-2 animate-pulse";
            selectedCameraInfo.textContent = "Mengakses Kamera...";

            // Start QR Scanner pointing to environment (rear-camera)
            await html5Qrcode.start(
                { facingMode: "environment" },
                {
                    fps: 12,
                    qrbox: function(width, height) {
                        const minDim = Math.min(width, height);
                        const qrSize = Math.floor(minDim * 0.65);
                        return {
                            width: qrSize,
                            height: qrSize
                        };
                    }
                },
                onScanSuccess,
                onScanFailure
            );

            cameraActive = true;
            btnStart.disabled = true;
            btnStart.className = "px-6 py-2.5 rounded-xl bg-purple-900 text-slate-500 font-medium text-sm transition-all cursor-not-allowed flex items-center gap-2";
            btnStop.disabled = false;
            btnStop.className = "px-5 py-2.5 rounded-xl bg-zinc-800 border border-zinc-700 hover:border-red-500 hover:bg-red-950/20 text-red-400 font-medium text-sm transition-all active:scale-95 flex items-center gap-2";
            
            // Hide standby background overlay
            scannerStandbyOverlay.classList.add('hidden');
            selectedCameraInfo.textContent = "Kamera Belakang Poco Aktif";

        } catch (err) {
            console.error("Camera access failed:", err);
            cameraStatusText.textContent = "Gagal Akses";
            cameraStatusText.className = "text-xl font-bold font-mono text-red-500 mt-2";
            selectedCameraInfo.textContent = "Perizinan Ditolak / Kamera Sedang Digunakan";
            alert("Gagal mematangkan inisialisasi kamera. Harap izinkan hak akses kamera pada dialog HP Poco Anda!");
        }
    }

    // ---- Stop Camera Scanning ----
    async function stopScanning() {
        if (!cameraActive) return;

        try {
            await html5Qrcode.stop();
            cameraActive = false;
            btnStart.disabled = false;
            btnStart.className = "px-6 py-2.5 rounded-xl bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 text-white font-medium text-sm transition-all shadow-md shadow-purple-600/25 active:scale-95 flex items-center gap-2";
            btnStop.disabled = true;
            btnStop.className = "px-5 py-2.5 rounded-xl bg-zinc-800 border border-zinc-700 hover:border-zinc-650 text-slate-300 font-medium text-sm transition-all disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-2";
            
            cameraStatusText.textContent = "Standby";
            cameraStatusText.className = "text-xl font-bold font-mono text-amber-400 mt-2";
            selectedCameraInfo.textContent = "Modul Kamera Dimatikan";
            
            // Reveal initial default screen overlay
            scannerStandbyOverlay.classList.remove('hidden');

        } catch (err) {
            console.error("Gagal menghentikan kamera:", err);
        }
    }

    // ---- File Upload Local QR Code Reader ----
    function scanFromGallery(e) {
        const file = e.target.files[0];
        if (!file) return;

        hideStatusBanners();

        // If scanning on camera is running, stop it first to prevent interference
        if (cameraActive) {
            stopScanning();
        }

        selectedCameraInfo.textContent = `Menganalisa File: ${file.name}`;
        
        // Use Html5Qrcode instance static-file reading engine
        html5Qrcode.scanFile(file, true)
            .then(qrCodeMessage => {
                onScanSuccess(qrCodeMessage);
                fileUploadInput.value = ''; // Reset input
            })
            .catch(err => {
                console.error("Gagal membaca gambar QR:", err);
                selectedCameraInfo.textContent = "File Tidak Mengandung QR!";
                alert("Gagal mendeteksi kode QR dari gambar ini. Sila pastikan gambar memiliki visual QR Code yang cukup jelas dan terang!");
                fileUploadInput.value = '';
            });
    }

    // ---- Success Scanning Callback ----
    function onScanSuccess(decodedText) {
        // Vibrate to simulate actual native scanner feedback
        if (navigator.vibrate) {
            navigator.vibrate(120);
        }

        // Auto-stop camera to allow inspection of results
        stopScanning();

        // Run Security Audit Framework (IOC analysis)
        analyzeSecurity(decodedText);
    }

    // --- Soft Scanning Failure (Ignore to prevent verbose browser alerts) ---
    function onScanFailure(error) {
        // Silent
    }

    // ---- Hide Status Alerts ----
    function hideStatusBanners() {
        malwareAlert.classList.add('hidden');
        resultContainer.classList.add('hidden');
    }

    // ---- SECURITY AUDIT ENGINE (Antivirus Logic) ----
    function analyzeSecurity(text) {
        const timestamp = new Date().toLocaleString('id-ID');
        const lowerData = text.toLowerCase();
        
        // Threat flag check
        let hasThreat = false;
        
        // 1. Check direct malware extensions contained anywhere inside the data payload
        for (const ext of dangerousExtensions) {
            if (lowerData.includes(ext)) {
                hasThreat = true;
                break;
            }
        }
        
        // 2. Extra protocol check / phishing detection: If url contains unusual double protocol scheme
        if (lowerData.includes('http://') && lowerData.includes('https://')) {
            hasThreat = true;
        }

        if (hasThreat) {
            // MALICIOUS DETECTED - FORBIDDEN LIVE PREVIEW & SHOW HIGH INTENSITY DANGER BANNER
            detectedMaliciousUrl.textContent = text;
            malwareAlert.classList.remove('hidden');
            
            // Save state to history array as Blocked Threat
            const record = {
                id: Date.now(),
                data: text,
                status: 'DANGER',
                date: timestamp
            };
            scanHistory.unshift(record);
            saveHistoryToStorage();

            // Auto navigate view context
            malwareAlert.scrollIntoView({ behavior: 'smooth' });

        } else {
            // BENIGN URL OR PLAIN DATA - PREPARE DISPLAY
            scannedRawText.textContent = text;
            
            // Handle HTTP/HTTPS URLs vs Plain text
            const isUrl = /^(https?:\/\/)/i.test(text.trim());
            
            if (isUrl) {
                // Config Safe State Level 0 (Green indicator link)
                threatIndicator.textContent = "AMAN - KREDENSIAL DISETUJUI";
                threatIndicator.className = "px-3 py-1 rounded-full text-xs font-mono font-bold bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 badge-success-glow";
                
                // Show view iframe live site safely using sandbox
                previewSection.classList.remove('hidden');
                previewIframe.src = text.trim();
                btnVisitUrl.href = text.trim();
                btnVisitUrl.style.display = 'inline-flex';
            } else {
                // Non URL context
                threatIndicator.textContent = "AMAN - DATA TEKS POLOS";
                threatIndicator.className = "px-3 py-1 rounded-full text-xs font-mono font-bold bg-purple-500/10 border border-purple-500/30 text-purple-300";
                
                previewSection.classList.add('hidden');
                previewIframe.src = "about:blank";
                btnVisitUrl.style.display = 'none';
            }

            resultContainer.classList.remove('hidden');

            // Save record as safe
            const record = {
                id: Date.now(),
                data: text,
                status: 'SAFE',
                date: timestamp
            };
            scanHistory.unshift(record);
            saveHistoryToStorage();

            // Auto navigate
            resultContainer.scrollIntoView({ behavior: 'smooth' });
        }

        // Synchronize UI view list logs
        renderHistory();
        updateStats();
    }

    // ---- Storage Sync ----
    function saveHistoryToStorage() {
        localStorage.setItem('qr_guard_history', JSON.stringify(scanHistory));
    }

    // ---- Redux style local rendering ----
    function renderHistory() {
        if (scanHistory.length === 0) {
            historyEmpty.classList.remove('hidden');
            historyList.innerHTML = '';
            return;
        }

        historyEmpty.classList.add('hidden');
        historyList.innerHTML = '';

        scanHistory.forEach((item) => {
            const isThreat = item.status === 'DANGER';
            
            const li = document.createElement('div');
            li.className = `p-4 rounded-2xl border ${
                isThreat 
                    ? 'border-red-900/40 bg-red-950/10 hover:bg-red-950/20' 
                    : 'border-purple-950 bg-luxury-card/50 hover:bg-purple-950/40'
            } transition-all duration-200 flex flex-col md:flex-row justify-between items-start md:items-center gap-3`;

            // Left container text
            const detailsDiv = document.createElement('div');
            detailsDiv.className = "grid gap-1 max-w-[85%]";
            
            // Header row with tag
            const headerRow = document.createElement('div');
            headerRow.className = "flex items-center gap-2";

            const tag = document.createElement('span');
            tag.className = `text-[9px] font-mono font-black tracking-widest px-2 py-0.5 rounded ${
                isThreat 
                    ? 'bg-red-500/20 text-red-400 border border-red-500/30' 
                    : 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
            }`;
            tag.textContent = isThreat ? 'BLOCKED MALWARE' : 'PASSED CLEAN';

            const timeLabel = document.createElement('span');
            timeLabel.className = "text-[10px] text-slate-500 font-mono";
            timeLabel.textContent = item.date;

            headerRow.appendChild(tag);
            headerRow.appendChild(timeLabel);

            const contentText = document.createElement('p');
            contentText.className = "font-mono text-xs break-all text-slate-300 font-semibold select-all mt-1";
            contentText.textContent = item.data;

            detailsDiv.appendChild(headerRow);
            detailsDiv.appendChild(contentText);

            // Right container actions (Delete button & Copy button)
            const rightBtnGroup = document.createElement('div');
            rightBtnGroup.className = "flex items-center gap-2 self-end md:self-center";

            // Copy Action
            const copyBtn = document.createElement('button');
            copyBtn.className = "p-2 rounded-lg bg-zinc-900 hover:bg-zinc-800 text-slate-400 hover:text-purple-300 transition-colors tooltip";
            copyBtn.innerHTML = `
                <svg xmlns="http://www.w3.org/2500/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M8.25 7.5V6.108c0-1.135.845-2.098 1.976-2.192.373-.03.748-.057 1.123-.08M15.75 18H18a2.25 2.25 0 002.25-2.25V6.108c0-1.135-.845-2.098-1.976-2.192a48.424 48.424 0 00-1.123-.08M3.75 18h11.25A2.25 2.25 0 0017.25 15.75V9a2.25 2.25 0 00-2.25-2.25H3.75A2.25 2.25 0 001.5 9v6.75A2.25 2.25 0 003.75 18z" />
                </svg>
            `;
            copyBtn.addEventListener('click', () => {
                navigator.clipboard.writeText(item.data);
                alert("Tautan disalin!");
            });

            // Specific Item Delete
            const deleteBtn = document.createElement('button');
            deleteBtn.className = "p-2 rounded-lg bg-zinc-900 hover:bg-rose-950/40 text-slate-400 hover:text-red-400 transition-colors";
            deleteBtn.innerHTML = `
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
                </svg>
            `;
            deleteBtn.addEventListener('click', () => {
                deleteHistoryItem(item.id);
            });

            rightBtnGroup.appendChild(copyBtn);
            rightBtnGroup.appendChild(deleteBtn);

            li.appendChild(detailsDiv);
            li.appendChild(rightBtnGroup);
            historyList.appendChild(li);
        });
    }

    // ---- Delete item ----
    function deleteHistoryItem(id) {
        scanHistory = scanHistory.filter(item => item.id !== id);
        saveHistoryToStorage();
        renderHistory();
        updateStats();
    }

    // ---- Clear history ----
    function clearAllHistory() {
        if (scanHistory.length === 0) return;
        
        const confirmClear = confirm("Apakah Anda yakin ingin menghapus seluruh log riwayat forensic scanner ini?");
        if (confirmClear) {
            scanHistory = [];
            saveHistoryToStorage();
            hideStatusBanners();
            renderHistory();
            updateStats();
        }
    }

    // ---- Copy to clipboard ----
    function copyScannedText() {
        const text = scannedRawText.textContent;
        if (!text) return;
        
        navigator.clipboard.writeText(text)
            .then(() => {
                alert("Berhasil disalin ke clipboard!");
            })
            .catch(err => {
                alert("Gagal menyalin item: " + err);
            });
    }

    // ---- Update Stats ----
    function updateStats() {
        const total = scanHistory.length;
        const threats = scanHistory.filter(item => item.status === 'DANGER').length;
        
        countTotalScans.textContent = total;
        countBlockedThreats.textContent = threats;
    }
});
