function api(url, options) {
    return fetch(url, options).then(function (response) {
        return response.json();
    });
}

function post(url, body) {
    return api(url, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(body)
    });
}

function money(value) {
    if (value === null || value === undefined) {
        return "-";
    }
    return Number(value).toFixed(2);
}

// 服务端返回的时间带微秒，先掐掉再交给 Date 解析
function toDate(value) {
    var text = String(value).split(".")[0].replace(" ", "T");
    return new Date(text);
}

function remainText(endTime) {
    var remain = toDate(endTime).getTime() - Date.now();
    if (remain <= 0) {
        return "已结束";
    }
    var totalSeconds = Math.floor(remain / 1000);
    var days = Math.floor(totalSeconds / 86400);
    var hours = Math.floor((totalSeconds % 86400) / 3600);
    var minutes = Math.floor((totalSeconds % 3600) / 60);
    var seconds = totalSeconds % 60;
    if (days > 0) {
        return days + " 天 " + hours + " 小时";
    }
    return pad(hours) + ":" + pad(minutes) + ":" + pad(seconds);
}

function remainSeconds(endTime) {
    return Math.floor((toDate(endTime).getTime() - Date.now()) / 1000);
}

function pad(number) {
    return number < 10 ? "0" + number : "" + number;
}

function escapeHtml(text) {
    if (text === null || text === undefined) {
        return "";
    }
    return String(text)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}

function fillNavbar() {
    var box = document.getElementById("navUser");
    if (!box) {
        return;
    }
    api("/api/users/me").then(function (result) {
        if (result.code === 200) {
            box.innerHTML =
                '<span class="balance">余额 ' + money(result.data.balance) + ' 元</span>' +
                '<span>' + escapeHtml(result.data.username) + '</span>' +
                '<a href="#" id="navLogout">退出</a>';
            document.getElementById("navLogout").onclick = function () {
                post("/api/users/logout", {}).then(function () {
                    window.location.href = "/";
                });
            };
        } else {
            box.innerHTML = '<a href="/login">登录</a><a href="/register">注册</a>';
        }
    });
}

document.addEventListener("DOMContentLoaded", fillNavbar);
