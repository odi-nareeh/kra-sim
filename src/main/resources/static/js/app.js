// Shared behaviour: mobile menu + dashboard PIN search (validation and loading state).

(function () {
    // ---------- Mobile navigation ----------
    const toggle = document.getElementById("navToggle");
    const nav = document.getElementById("mainNav");

    if (toggle && nav) {
        toggle.addEventListener("click", () => {
            const open = nav.classList.toggle("open");
            toggle.setAttribute("aria-expanded", String(open));
        });

        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape" && nav.classList.contains("open")) {
                nav.classList.remove("open");
                toggle.setAttribute("aria-expanded", "false");
                toggle.focus();
            }
        });
    }

    // ---------- Dashboard search ----------
    const form = document.getElementById("pinSearchForm");
    if (!form) return;

    const input = document.getElementById("pin");
    const button = document.getElementById("searchBtn");
    const label = button.querySelector(".btn-label");
    const error = document.getElementById("pin-error");

    // Same format the registration page enforces: A or P, 9 digits, a letter
        // Same format the registration page enforces: A or P, 9 digits, a letter
    const PIN_PATTERN = /^[AP]\d{9}[A-Z]$/;

    // Returns a specific message for the first problem found, or "" if the PIN is valid
    function pinProblem(pin) {
        if (pin === "") return "Enter a KRA PIN to search.";
        if (pin.length !== 11) return "A KRA PIN has 11 characters. You entered " + pin.length + ".";
        if (!/^[AP]/.test(pin)) return "A KRA PIN must start with the letter A or P.";
        if (!/^.\d{9}/.test(pin)) return "The middle part must be 9 digits. Example: A112233445Z.";
        if (!/[A-Z]$/.test(pin)) return "A KRA PIN must end with a letter. Example: A112233445Z.";
        return PIN_PATTERN.test(pin) ? "" : "Invalid PIN. Example of a valid PIN: A112233445Z.";
    }

    function showError(message) {
        error.textContent = message;
        error.hidden = false;
        input.setAttribute("aria-invalid", "true");
        input.focus();
    }

    function clearError() {
        error.hidden = true;
        error.textContent = "";
        input.removeAttribute("aria-invalid");
    }

    function setLoading(isLoading) {
        button.disabled = isLoading;
        button.classList.toggle("is-loading", isLoading);
        form.setAttribute("aria-busy", String(isLoading));
        label.textContent = isLoading ? "Searching…" : "Search";
    }

    input.addEventListener("input", clearError);

    form.addEventListener("submit", (event) => {
        const pin = input.value.trim().toUpperCase();
        input.value = pin;

        const problem = pinProblem(pin);
        if (problem) {
            event.preventDefault();
            showError(problem);
            return;
        }

        clearError();
        setLoading(true); // also blocks double submission
    });

    // Restore the button if the user comes back with the browser's Back button
    window.addEventListener("pageshow", () => setLoading(false));
})();
// Copy-to-clipboard buttons: data-copy-target is the id of the element holding the text.
(function () {
    document.querySelectorAll("[data-copy-target]").forEach((button) => {
        button.addEventListener("click", async () => {
            const target = document.getElementById(button.dataset.copyTarget);
            if (!target) return;
            try {
                await navigator.clipboard.writeText(target.textContent.trim());
                button.textContent = "Copied";
            } catch (err) {
                // Fallback: select the text so the user can press Ctrl+C
                const range = document.createRange();
                range.selectNodeContents(target);
                const selection = window.getSelection();
                selection.removeAllRanges();
                selection.addRange(range);
                button.textContent = "Press Ctrl+C";
            }
        });
    });
})();