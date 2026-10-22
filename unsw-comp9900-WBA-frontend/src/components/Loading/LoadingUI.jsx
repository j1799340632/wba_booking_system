import './index.css';

export default function LoadingUI({ text, dynamicStyle }) {
    return (
        <div className="target-loading-wrapper" style={dynamicStyle}>
            <div className="spinner-ring"></div>
            {text && <span className="loading-text">{text}</span>}
        </div>
    );
}
