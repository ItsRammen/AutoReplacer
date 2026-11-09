# AutoReplacer

[English](README.md) · **繁體中文（台灣）**

讓只支援 **Auto+ Show**（Garmin USB Link）的車機螢幕也能使用 Android Auto。

AutoReplacer 會在你的手機上執行 Android Auto，將畫面編碼後透過 USB 傳送到車機。車機上的觸控會回傳到手機，讓你直接在車機螢幕上操作 Android Auto。音訊與通話則透過藍牙傳輸。

本專案是 **[Open Headunit](https://github.com/andreknieriem/open-headunit) 的 hard fork**（Open Headunit 本身承續自 Michael A. Reid 的 [Headunit](https://github.com/mikereidis/headunit)），專為單一用途改寫：作為 Auto+ Show 車機的手機端。授權為 **GNU AGPL v3**。

> **狀態：可運作的雛形，僅在一台車與一支手機上測試過。** 測試環境為台灣的 Toyota Corolla Cross（配備 Auto+ Show 車機）、Samsung SM-S9480（Android 16）、Android Auto 17.9。可能還有不少粗糙之處，使用前請先閱讀[已知限制](#已知限制)。
>
> **與 Garmin、Google、Toyota、Auto+ Show 皆無關聯。** 本專案不包含任何 Garmin 的程式碼或素材；USB 通訊協定是從觀察中推敲出來的（見 [How this was found out](docs/how-it-was-found.md)，英文）。
>
> **安全提醒。** Android Auto 設計上可在行駛時使用，並有自己的限制，但觸控螢幕仍會分散注意力。車輛行駛中請使用語音控制，並在出發前先設定好目的地。AutoReplacer 不會試圖覆寫車機回報的任何行車限制。

## 功能

- **在車機上顯示 Android Auto。** Android Auto 在手機上執行，就像手機本身是它的車機。AutoReplacer 取得它的影像，重新編碼成車機可用的格式，再透過 USB 傳送。
- **將車機的觸控傳給 Android Auto。** 點擊與拖曳都可使用；兩指縮放則不行，因為車機一次只傳送一根手指。
- **觸控校正。** 車機回報的觸控座標與它的畫面並不一致。引導式校正（在車機螢幕上點選九個十字）會量測對應關係並加以修正。
- **配對車機的藍牙**，用於音訊與通話，並顯示是否已連線。
- **USB 中斷後自動恢復。** 它會保留狀態、在車機上顯示等待畫面，並在傳輸線恢復後重新連線 Android Auto。
- **用白話說明目前狀況。** 一則通知與一張狀態卡片會以易懂的文字回報車機連線、Android Auto、觸控與藍牙的狀態。
- **自備記錄檔**，可儲存後附在問題回報中。

它**不需要** Root、Shizuku 或無障礙服務，也不會錄製你的螢幕。

## 需求

- 配備 **Auto+ Show／Garmin USB Link** 車機的汽車（車機會顯示「Auto+ Show」，通常搭配 Garmin 的 Auto+ Show 手機應用程式使用）。
- 已安裝 **Android Auto** 的 Android 手機。測試版本為 Android 16 上的 Android Auto 17.9。此方法仰賴 Android Auto 的「主機伺服器」功能（需要 Android Auto 17.4 或更新版本）。
- 已開啟**開發人員選項**與 **USB 偵錯**。車機會透過 ADB 連線來設定它的觸控輔助程式。
- USB 資料傳輸線（不可以是僅供充電的線材），並使用車上支援 Auto+ Show 的 USB 連接埠。
- 已與車機配對的藍牙，用於音訊與通話。
- 允許通知，狀態通知才能顯示。

## 安裝與設定

1. 從 [Releases](../../releases) 頁面**安裝** APK。套件名稱為 `com.andrerinas.headunitrevived.autoreplacer`，因此可與 Open Headunit 並存。除錯版使用除錯金鑰簽署，若要覆蓋安裝以其他金鑰簽署的版本，需要先解除安裝。
2. **開啟應用程式一次**，完成簡短的設定。它會檢查 Android Auto、開發人員選項、USB 偵錯與通知，並要求藍牙（「附近的裝置」）權限。
3. **開啟 Android Auto 的開發人員模式。** 在 Android Auto 設定中，連續點選版本號碼約十次，然後開啟「開發人員設定」。
4. **啟動主機伺服器。** 在 Android Auto 的「開發人員設定」中，開啟選單並選擇 **Start head unit server**（啟動主機伺服器）。請在插入傳輸線**之前**完成。Android Auto 可能在一次連線結束後停止伺服器，因此你可能需要再啟動一次。若沒有任何程式在監聽，應用程式會顯示琥珀色警告，並提供「開啟 Android Auto」按鈕。
5. **插入傳輸線。** 將手機接上車上的 Auto+ Show 連接埠。第一次插入時，Android 會詢問由哪個應用程式處理這個 USB 配件：請選擇 AutoReplacer，並勾選「預設使用」。
6. 如果手機出現車機的 **ADB 授權提示**，請允許。勾選「一律允許」代表這台車可在手機上執行 ADB 指令，因此只應在你擁有或信任的車上這麼做（見[隱私與安全](#隱私與安全)）。
7. 車機上會先出現**等待畫面**（轉圈圖示與目前的步驟），接著出現 Android Auto。
8. **校正一次觸控。** 在 Android Auto 顯示時，開啟主畫面卡片上的 ⋮ 選單，或前往「設定 → 車機觸控」，選擇「校正車機觸控」，然後依序點選九個十字的中心。

### 主畫面

主畫面卡片會顯示車機連線的狀態（等待車機、連線中、Android Auto 啟動中、已顯示於車機、車機已中斷連線），以及**觸控**與**藍牙**各一行狀態。傳輸線已插入且沒有連線進行時，會出現**重新連線**按鈕。**⋮** 選單中有「儲存記錄檔」與「校正車機觸控」。通知中顯示相同的狀態，並附有「儲存記錄檔」與「標記」按鈕。

## 語言

應用程式提供 **English** 與**繁體中文（台灣）**，會跟隨手機的語言，也可在「設定」中自行選擇。本 README 另有[英文版](README.md)。`docs/` 底下的頁面僅有英文。

## 遇到問題時

| 你看到的情況 | 該怎麼做 |
| --- | --- |
| 琥珀色的**請先啟動 Android Auto 的伺服器** | 在 Android Auto 的開發人員設定中啟動主機伺服器，然後再插入傳輸線。 |
| **Android Auto 沒有回應** | 將主機伺服器停止後重新啟動。一次連線結束後，它可能沉默一分鐘或更久。 |
| 車機螢幕全黑，或停在等待畫面 | 拔除後重新插入傳輸線，並確認伺服器正在執行。 |
| **與車機的 USB 連線已中斷** | 傳輸線或連接埠中斷，或車機重新啟動了它的 USB。應用程式會等待 90 秒後重新連線；若沒有，請重新插拔。 |
| 觸控沒有反應，或靠近邊緣時有偏差 | 重新校正觸控（「⋮ → 校正車機觸控」）。若觸控一直沒有出現，請儲存記錄檔。 |

要儲存記錄檔，請開啟 **⋮ → 儲存記錄檔**（或點通知中的「儲存記錄檔」），檔案會存到「下載」資料夾。如何閱讀記錄檔，請見 [Troubleshooting and logs](docs/troubleshooting.md)（英文）。

## 隱私與安全

- 車機需要在你的手機上執行 **ADB 指令**才能設定觸控。只在你擁有或信任的車上勾選「一律允許」。Android 會讓超過七天未使用的「一律允許」金鑰失效。
- 應用程式只會在 **127.0.0.1** 開啟 TCP 連接埠，因此無法從網路連入。
- 儲存的記錄檔含有你的手機型號與**遮蔽過**的藍牙位址（僅保留最後兩個位元組）。張貼前請先檢查內容。

## 已知限制

- **僅在一台車與一支手機上測試過。** 其他 Auto+ Show 車款、手機與 Android 版本尚未測試。觸控校正是依手機而定，而非依車輛。
- **僅支援單指觸控。** 車機的觸控連線一次只傳送一個點，因此無法使用兩指縮放等手勢。可改用 Android Auto 畫面上的縮放按鈕。
- **必須手動啟動 Android Auto 的主機伺服器**，而且 Android Auto 可能在一次連線結束後停止它。AutoReplacer 無法替你啟動。
- **USB 重置會結束 Android Auto 的連線**，因此中斷一次代表需要重新連線 Android Auto，而不只是傳輸線。
- **觸控有時不會出現**，原因不明。
- **USB 偶爾中斷。** 在測試環境中確實發生過。可能是傳輸線、連接埠或車機端的原因；應用程式只能協助恢復，無法避免。
- **藍牙通話。** 手機無法自行連接耳機（通話）設定檔；若尚未連線，請在手機的藍牙設定中選擇車機。
- **早期軟體。** 使用除錯簽署的版本，沒有上架商店，也沒有自動更新。

## 進一步了解

以下頁面僅有英文：

| 頁面 | 內容 |
| --- | --- |
| [Troubleshooting and logs](docs/troubleshooting.md) | 閱讀事件記錄，以及常見訊息的意義 |
| [How AutoReplacer works](docs/how-it-works.md) | 手機上的 Android Auto、影像、USB Link、觸控、藍牙、重新連線 |
| [How this was found out](docs/how-it-was-found.md) | 研究過程、走過的彎路與證據 |
| [Building from source](docs/building.md) | 建置、測試、翻譯與專案結構 |
| [Making a release](docs/releasing.md) | 打標籤與 GitHub 上的建置 |
| [All documentation](docs/README.md) | 索引，包含原始研究筆記 |
| [Changelog](openheadunit/CHANGELOG-AUTOREPLACER.md) | 各版本的變更 |

## 回報問題

請開一個 issue，並附上：

1. 你在**車機**與**手機**上看到的情況，以及大約的時間。
2. **儲存的記錄檔**（「⋮ → 儲存記錄檔」，位於「下載」）。請先檢查內容。
3. 手機型號、Android 版本、Android Auto 版本，以及汽車與車機型號。

若是 USB 中斷或觸控沒出現，請註明你當時做了什麼（拔掉、轉動傳輸線、發動車輛、開始行駛）。在事情發生的當下點通知中的「標記」，可以在記錄檔中留下時間戳記，方便把記錄檔和實際情況對應起來。

## 授權與致謝

- **AutoReplacer** 是 Open Headunit 的 **hard fork**，不會與上游保持同步，也不打算合併回去。變更內容列於 [`openheadunit/UPSTREAM.md`](openheadunit/UPSTREAM.md)，其中也記錄了匯入的起點。
- 以 **GNU Affero 通用公共授權條款第 3 版**（AGPL v3，見 [`LICENSE`](LICENSE)）釋出。任何散布此軟體的建置版本、或讓他人透過網路使用修改版的人，都必須以相同授權提供完整的對應原始碼。
- **Open Headunit**：André Rinas 與貢獻者，<https://github.com/andreknieriem/open-headunit>。
- **Headunit**：Michael A. Reid，<https://github.com/mikereidis/headunit>，是最初的 Android Auto 車機程式碼與通訊協定研究（聲明：[`openheadunit/COPYRIGHT_MICHAEL_REID_GPLv3AFFERO.txt`](openheadunit/COPYRIGHT_MICHAEL_REID_GPLv3AFFERO.txt)）。
- 第三方函式庫各自保有其授權。
- 「Garmin」、「Auto+ Show」、「Android Auto」、「Toyota」及其他名稱屬於各自的擁有者，在此僅用於說明本軟體可搭配的對象。這裡描述的 USB 通訊協定，是為了互通性，而從觀察車輛以及公開分析可自由下載的應用程式所得出。
