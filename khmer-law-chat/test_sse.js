const bufStr = "data:[SESSION:123]\n\ndata:Notice\n\n";
let buf = bufStr;
let eventData = [];
let nl;
while ((nl = buf.indexOf('\n')) !== -1) {
  let line = buf.slice(0, nl).replace(/\r$/, '');
  buf = buf.slice(nl + 1);
  if (line === '') {
    if (eventData.length > 0) {
      let chunk = eventData.join('\n');
      if (chunk.startsWith('[SESSION:')) {
        const end = chunk.indexOf(']');
        chunk = chunk.slice(end + 1);
      }
      if (chunk) {
        console.log("DELTA:", chunk);
      }
      eventData = [];
    }
  } else if (line.startsWith('data:')) {
    let chunk = line.slice(5);
    if (chunk.startsWith(' ')) chunk = chunk.slice(1);
    eventData.push(chunk);
  }
}
