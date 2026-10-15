// eTIMS POS simulator: live VAT preview + POST to the API with the device's X-API-Key.

const VAT_RATE = 0.16;

const form = document.getElementById("invoiceForm");
const apiKeyInput = document.getElementById("apiKey");
const pinInput = document.getElementById("taxpayerPin");
const amountInput = document.getElementById("amountBeforeTax");
const previewVat = document.getElementById("previewVat");
const previewTotal = document.getElementById("previewTotal");
const resultBox = document.getElementById("result");
const jsonBox = document.getElementById("responseJson");

function formatMoney(value) {
    return value.toLocaleString("en-KE", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// Live preview as the user types the amount
amountInput.addEventListener("input", () => {
    const amount = parseFloat(amountInput.value) || 0;
    const vat = amount * VAT_RATE;
    previewVat.textContent = formatMoney(vat);
    previewTotal.textContent = formatMoney(amount + vat);
});

function showResult(message, isError) {
    resultBox.textContent = message;
    resultBox.className = "alert " + (isError ? "alert-error" : "alert-success");
}

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    jsonBox.classList.add("hidden");

    const payload = {
        taxpayerPin: pinInput.value.trim().toUpperCase(),
        amountBeforeTax: parseFloat(amountInput.value)
    };

    try {
        const response = await fetch("/api/v1/invoices/submit", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "X-API-Key": apiKeyInput.value.trim()
            },
            body: JSON.stringify(payload)
        });
        const data = await response.json();

        jsonBox.textContent = JSON.stringify(data, null, 2);
        jsonBox.classList.remove("hidden");

        if (response.ok) {
           showResult("Invoice " + data.controlUnitInvoiceNo + " accepted. VAT of KSh "
            + formatMoney(data.vatAmount) + " was added to the taxpayer ledger.", false);
        } else if (response.status === 401) {
            showResult("The device API key was rejected. Check the key and try again.", true);
        } else {
            showResult(data.error || "The server rejected the invoice.", true);
        }
    } catch (err) {
        showResult("Could not reach the server: " + err.message, true);
    }
});