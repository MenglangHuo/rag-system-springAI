const SAMPLE = `Under the **Cambodian Labour Law (1997, amended)**, here are the key points:

### Termination
- Employers must provide **written notice** based on seniority (7 days to 3 months).
- **Severance** ("indemnity for dismissal") is required for unfixed-duration contracts unless dismissal is for serious misconduct.

### Annual Leave
- Employees are entitled to **1.5 days of paid leave per month** worked → **18 days/year** minimum.
- Add **+1 day per 3 years** of seniority.

### Overtime
- Standard: **48 hours/week**, 8 hours/day.
- Overtime paid at **150%** (day) and **200%** (night/holidays).

### Maternity
- **90 days** paid maternity leave at **50% wages** after 1 year of service.

> ⚖️ This is general guidance. Always consult a licensed Cambodian labour lawyer for specific cases.`;
export async function simulatedStream(prompt, h) {
    const text = SAMPLE;
    const tokens = text.split(/(\s+)/);
    try {
        for (const t of tokens) {
            if (h.signal?.aborted)
                return;
            await new Promise(r => setTimeout(r, 18 + Math.random() * 35));
            h.onDelta(t);
        }
        h.onDone();
    }
    catch (e) {
        h.onError?.(e);
    }
}
/** Real SSE example — wire to your backend when ready. */
export async function sseStream(endpoint, body, h) {
    try {
        const resp = await fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
            signal: h.signal,
        });
        if (!resp.ok || !resp.body)
            throw new Error(`HTTP ${resp.status}`);
        const reader = resp.body.getReader();
        const dec = new TextDecoder();
        let buf = '';
        let eventData = [];
        while (true) {
            const { done, value } = await reader.read();
            if (done)
                break;
            const text = dec.decode(value, { stream: true });
            console.log('RECEIVED CHUNK:', text);
            buf += text;
            let nl;
            while ((nl = buf.indexOf('\n')) !== -1) {
                let line = buf.slice(0, nl).replace(/\r$/, '');
                buf = buf.slice(nl + 1);
                if (line === '') {
                    // end of event
                    if (eventData.length > 0) {
                        let chunk = eventData.join('\n');
                        if (chunk.startsWith('[SESSION:')) {
                            const end = chunk.indexOf(']');
                            if (end !== -1) {
                                const sessionId = chunk.slice(9, end);
                                if (h.onSessionId)
                                    h.onSessionId(sessionId);
                                chunk = chunk.slice(end + 1);
                            }
                        }
                        if (chunk) {
                            console.log('EMITTING DELTA:', chunk);
                            h.onDelta(chunk);
                        }
                        eventData = [];
                    }
                }
                else if (line.startsWith('data:')) {
                    let chunk = line.slice(5);
                    eventData.push(chunk);
                }
            }
        }
        h.onDone();
    }
    catch (e) {
        h.onError?.(e);
    }
}
