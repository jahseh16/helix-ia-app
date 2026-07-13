/**
 * Helix - Termux WebSocket Shell Server
 * 
 * Instructions:
 * 1. Install Node.js in Termux: pkg install nodejs
 * 2. Create a folder: mkdir helix && cd helix
 * 3. Initialize & install ws: npm init -y && npm install ws
 * 4. Save this script as 'server.js'
 * 5. Start the server: node server.js
 */

const WebSocket = require('ws');
const { exec } = require('child_process');

const PORT = 8080;
const wss = new WebSocket.Server({ port: PORT });

const boldGreen = '\x1b[1m\x1b[32m';
const reset = '\x1b[0m';

console.log(`=========================================`);
console.log(`     HELIX TERMUX WEB-SHELL ACTIVE      `);
console.log(`=========================================`);
console.log(`Server listening on ws://localhost:${PORT}`);
console.log(`Waiting for Helix App connections...\n`);

wss.on('connection', (ws, req) => {
    const ip = req.socket.remoteAddress;
    console.log(`[+] Client connected from ${ip}`);
    
    // Welcome payload
    ws.send(JSON.stringify({
        type: 'status',
        data: `Connected to Termux shell at ${new Date().toLocaleTimeString()}\nSystem: ${process.platform} (${process.arch})\n`
    }));

    ws.on('message', (message) => {
        let command = '';
        try {
            // Support both JSON formats and raw string commands
            const payload = JSON.parse(message);
            command = payload.command || payload;
        } catch (e) {
            command = message.toString().trim();
        }

        if (!command) return;

        console.log(`[*] Executing command: "${command}"`);
        
        ws.send(JSON.stringify({
            type: 'input',
            data: `$ ${command}\n`
        }));

        // Execute command in the local Termux environment
        const processRef = exec(command, { shell: '/bin/sh' });

        processRef.stdout.on('data', (data) => {
            ws.send(JSON.stringify({
                type: 'stdout',
                data: data.toString()
            }));
        });

        processRef.stderr.on('data', (data) => {
            ws.send(JSON.stringify({
                type: 'stderr',
                data: data.toString()
            }));
        });

        processRef.on('close', (code) => {
            ws.send(JSON.stringify({
                type: 'exit',
                data: `\n[Process completed with exit code ${code}]\n`
            }));
            console.log(`[✓] Command execution finished with code ${code}\n`);
        });

        processRef.on('error', (err) => {
            ws.send(JSON.stringify({
                type: 'error',
                data: `Execution error: ${err.message}\n`
            }));
            console.log(`[!] Execution error: ${err.message}\n`);
        });
    });

    ws.on('close', () => {
        console.log(`[-] Client disconnected`);
    });
});
