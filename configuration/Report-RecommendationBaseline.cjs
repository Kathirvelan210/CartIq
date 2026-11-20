'use strict';

const fs = require('node:fs');
const path = require('node:path');

const reports = process.argv[2];
if (!reports) {
  console.error('Usage: node configuration/Report-RecommendationBaseline.cjs <surefire-reports>');
  process.exit(2);
}
const dataset = JSON.parse(fs.readFileSync(path.join(__dirname, '../docs/recommendation-regression-dataset.json'), 'utf8'));
const tasks = {};
const results = [];
for (const entry of dataset.cases) {
  const report = path.join(reports, `TEST-${entry.suite}.xml`);
  const xml = fs.existsSync(report) ? fs.readFileSync(report, 'utf8') : '';
  const name = entry.test.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const row = xml.match(new RegExp(`<testcase\\b(?=[^>]*\\bname="${name}")[^>]*(?:\\/>|>[\\s\\S]*?<\\/testcase>)`))?.[0];
  const status = !row || /<skipped\b/.test(row) ? 'NOT_TESTED' : /<(failure|error)\b/.test(row) ? 'FAIL' : 'PASS';
  tasks[entry.task] ??= {planned:0, PASS:0, FAIL:0, NOT_TESTED:0};
  tasks[entry.task].planned++;
  tasks[entry.task][status]++;
  results.push({id:entry.id,expected:entry.expected,status});
}
console.log(JSON.stringify({version:dataset.version,kind:dataset.kind,tasks,results},null,2));
if (results.some(entry=>entry.status!=='PASS'))process.exitCode=1;
