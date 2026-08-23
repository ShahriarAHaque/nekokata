#!/usr/bin/env node
/** static smoke checks for www + android packaging */
const { readFileSync, existsSync } = require('fs');
const { join } = require('path');

const root = join(__dirname, '..');
const www = join(root, 'www');
const html = readFileSync(join(www, 'index.html'), 'utf8');

let failed = 0;
function ok(msg) { console.log(`  OK ${msg}`); }
function fail(msg) { console.error(`  FAIL ${msg}`); failed++; }

console.log('nekokata Android smoke checks\n');

const cssLinks = [...html.matchAll(/href="(styles\/[^"]+\.css)"/g)].map(m => m[1]);
for (const rel of cssLinks) {
  if (existsSync(join(www, rel))) ok(`CSS ${rel}`);
  else fail(`Missing CSS ${rel}`);
}

for (const id of [
  'screen-home', 'screen-fitness', 'screen-stats', 'screen-nutrition',
  'screen-measurements', 'screen-profile', 'screen-water', 'screen-data', 'screen-todo',
  'carouselBar', 'statGrid', 'dayPanelBody', 'fitnessChecklist', 'navDrawer',
]) {
  if (html.includes(`id="${id}"`)) ok(`DOM #${id}`);
  else fail(`Missing DOM #${id}`);
}

for (const fn of [
  'showScreen', 'carouselStep', 'renderHome', 'renderFitness',
  'initWidgetBridge', 'syncHomeScreenWidgetState', 'setupAndroidBackButton',
]) {
  if (html.includes(`function ${fn}`)) ok(`JS ${fn}`);
  else fail(`Missing JS ${fn}`);
}

if (html.includes('WidgetSync')) ok('WidgetSync bridge');
else fail('WidgetSync bridge');

if (existsSync(join(root, 'android', 'app', 'src', 'main', 'assets', 'public', 'index.html'))) {
  ok('Android assets synced');
} else fail('Android assets not synced');

for (const j of ['MainActivity.java', 'WidgetSyncPlugin.java', 'NekokataWidgetProvider.java']) {
  if (existsSync(join(root, 'android', 'app', 'src', 'main', 'java', 'com', 'nekokata', 'app', j))) {
    ok(`Java ${j}`);
  } else fail(`Missing ${j}`);
}

console.log(failed ? `\n${failed} failed` : '\nAll static checks passed');
process.exit(failed ? 1 : 0);
