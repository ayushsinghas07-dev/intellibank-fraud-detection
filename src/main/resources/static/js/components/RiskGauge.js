// Custom SVG Animated Risk Gauge Component
export const RiskGauge = {
    render(score, level, size = 180) {
        let color = '#10b981'; // LOW Green
        if (level === 'MEDIUM') color = '#f59e0b';
        if (level === 'HIGH') color = '#f97316';
        if (level === 'CRITICAL') color = '#ef4444';

        const strokeWidth = 14;
        const radius = (size - strokeWidth) / 2;
        const circumference = Math.PI * radius; // Half arc (180 deg)
        const offset = circumference - (score / 100) * circumference;

        return `
            <div class="risk-gauge-container" style="position: relative; width: ${size}px; height: ${size / 1.6}px; text-align: center; margin: 0 auto;">
                <svg width="${size}" height="${size / 1.5}" viewBox="0 0 ${size} ${size / 2 + strokeWidth}">
                    <!-- Background Arc -->
                    <path d="M ${strokeWidth/2}, ${size/2} A ${radius} ${radius} 0 0 1 ${size - strokeWidth/2} ${size/2}"
                          fill="none" stroke="var(--border-color)" stroke-width="${strokeWidth}" stroke-linecap="round"/>
                    
                    <!-- Progress Arc -->
                    <path d="M ${strokeWidth/2}, ${size/2} A ${radius} ${radius} 0 0 1 ${size - strokeWidth/2} ${size/2}"
                          fill="none" stroke="${color}" stroke-width="${strokeWidth}" stroke-linecap="round"
                          stroke-dasharray="${circumference}" stroke-dashoffset="${offset}"
                          style="transition: stroke-dashoffset 800ms ease-out, stroke 300ms;"/>
                </svg>
                <div style="position: absolute; bottom: 0; left: 50%; transform: translateX(-50%);">
                    <div style="font-size: 2.25rem; font-weight: 800; color: var(--text-primary); line-height: 1;">${score}</div>
                    <div style="font-size: 0.75rem; font-weight: 700; color: ${color}; text-transform: uppercase; margin-top: 2px;">${level}</div>
                </div>
            </div>
        `;
    }
};
