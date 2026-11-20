'use strict';

// Run inside the existing AI process environment or a shell using the same protected injection.
// Configuration presence only. This script has no network client.
const present = key => process.env[key]?.trim() ? 'PRESENT' : 'MISSING';
const model = process.env.GEMINI_MODEL || '';
const checks = {
  generationEnabled: process.env.CARTIQ_RECIPE_GENERATION_ENABLED === 'true',
  serviceCredential: present('AI_SERVICE_SECRET'),
  geminiCredential: present('GEMINI_API_KEY'),
  model: !model ? 'MISSING' : /^gemini-[0-9]+\.[0-9]+-flash(?:-lite)?$/.test(model) ? 'PRESENT' : 'INVALID',
  plannedOperation: 'One authenticated POST /api/me/recipe-intelligence/generate through Spring and private FastAPI',
  prompt: 'Suggest a quick South Indian breakfast for two.',
  usePantry: false,
  maximumGeminiRequests: 1,
  requestsSent: 0,
  authorization: 'PENDING'
};
console.log(JSON.stringify(checks, null, 2));
