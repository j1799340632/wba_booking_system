import './index.css'
import {useState, useEffect} from "react";
import {Lock} from "lucide-react";
import {notify} from "@/utils/notify.js";
import {getEmailVerificationCode} from "@/config/request/api/verifyApi.js";

/**
 * ChangePasswordModal
 *
 * @param targetUserId - Target user id
 * @param onClose - Close callback
 * @param {function} onChangePassword - Submit password API
 * @param {number} [initialCooldown=60] - Countdown timer for obtaining verification code (default 60s)
 */
export const ChangePasswordModal = (
    {
        targetUserId,
        onClose,
        onChangePassword,
        initialCooldown = 60,
    }
) => {
    const [code, setCode] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");

    // Flow state
    const [loading, setLoading] = useState(false);
    const [cooldown, setCooldown] = useState(0);

    // Cooldown
    useEffect(() => {
        if (cooldown <= 0) return;
        const timer = setInterval(() => {
            setCooldown((prev) => prev - 1);
        }, 1000);
        return () => clearInterval(timer);
    }, [cooldown]);

    // Reset
    const resetForm = () => {
        setCode("");
        setNewPassword("");
        setConfirmPassword("");
        setLoading(false);
    };

    const handleClose = () => {
        resetForm();
        onClose?.();
    };

    // Send verification code
    const handleSendCode = async () => {
        if (cooldown > 0 || loading) return;

        setLoading(true);

        getEmailVerificationCode()?.then(() => {
            notify.success("Verification code sent to your email");
            setCooldown(initialCooldown);
            setLoading(false)
        }).catch(() => setLoading(false))
    };

    // Submit new password
    const handleChangePassword = e => {
        e.preventDefault();

        if (!newPassword || !confirmPassword || !code) {
            notify.warning("Please fill in all fields!");
            return;
        }

        if (newPassword !== confirmPassword) {
            notify.warning("Passwords do not match!");
            return;
        }

        const data = {
            targetUserId,
            newPassword,
            verifyCode: code
        }

        setLoading(true);
        onChangePassword?.(data).then(() => {
            notify.success("Password changed successfully!");
            handleClose();
        }).catch(() => {
            setLoading(false);
        });
    };

    return (
        <div className="settings-modal-body">
            <form
                className="settings-form"
                onSubmit={handleChangePassword}
                autoComplete="off"
            >
                <input
                    type="text"
                    name="fakeusernameremembered"
                    className="settings-autofill-trap"
                />
                <input
                    type="password"
                    name="fakepasswordremembered"
                    className="settings-autofill-trap"
                />

                <p className="settings-card-hint">
                    For your security, verify your identity before changing your
                    password.
                </p>

                <label className="settings-label" htmlFor="verification-code">
                    Email Verification Code
                    <div className="settings-code-row">
                        <input
                            id="verification-code"
                            name="wba-verification-code"
                            type="text"
                            inputMode="numeric"
                            className="settings-input"
                            value={code}
                            onChange={(e) => setCode(e.target.value)}
                            placeholder="Enter the code sent to your email"
                            autoComplete="one-time-code"
                            required
                        />

                        <button
                            type="button"
                            className="settings-send-code-button"
                            onClick={handleSendCode}
                            disabled={cooldown > 0 || loading}
                        >
                            {cooldown > 0 ? `Resend (${cooldown}s)` : "Send Code"}
                        </button>
                    </div>
                </label>

                <label className="settings-label" htmlFor="new-password">
                    New Password
                    <input
                        id="new-password"
                        type="password"
                        className="settings-input"
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        autoComplete="new-password"
                        minLength={6}
                        required
                    />
                </label>

                <label className="settings-label" htmlFor="confirm-password">
                    Confirm New Password
                    <input
                        id="confirm-password"
                        type="password"
                        className="settings-input"
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        autoComplete="new-password"
                        minLength={6}
                        required
                    />
                </label>

                <div className="settings-form-actions">
                    <button
                        type="submit"
                        className="settings-submit-button"
                        disabled={loading}
                    >
                        <Lock size={16} />
                        {loading ? "Changing..." : "Change Password"}
                    </button>
                </div>
            </form>
        </div>
    );
};
