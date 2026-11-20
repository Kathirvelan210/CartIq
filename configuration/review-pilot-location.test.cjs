const {test} = require('node:test');
const {equal} = require('node:assert/strict');
const {spawnSync} = require('node:child_process');
const {join} = require('node:path');
const {reviewDeliveryArea} = require('./Review-PilotLocation.cjs');

// Synthetic addresses only. The proposed point's real locality is unverified.
const scope = {pin:'641008', latitude:10.942972, longitude:76.953417, city:'Coimbatore', state:'Tamil Nadu'};
const area = {postcode:scope.pin, city:scope.city, state:scope.state, locality:'Fixture Ward', district:'Fixture District', countryCode:'in'};
const reverse = {...area, latitude:scope.latitude, longitude:scope.longitude, approximate:false};

test('one reverse-confirmed locality among multiple PIN results can proceed to human review', () => {
  equal(reviewDeliveryArea([{...area, locality:'Different Ward'}, {...area, locality:'  fixture  ward '}], reverse, scope),
    'CONSISTENT_FOR_HUMAN_REVIEW');
});

test('rejects conflicting country, PIN, locality evidence and wrong-point reverse results', () => {
  for (const conflict of [{countryCode:'lk'}, {postcode:'641009'}, {locality:''}, {locality:scope.city},
    {state:'Different State'}, {city:'Different City'}, {latitude:10.9}, {approximate:true}]) {
    equal(reviewDeliveryArea([area], {...reverse, ...conflict}, scope), 'REVERSE_AREA_UNCONFIRMED');
  }
  equal(reviewDeliveryArea([{...area, district:'Other District'}], reverse, scope), 'PIN_LOCALITY_UNCONFIRMED');
});

test('holds missing or ambiguous PIN localities for manual evidence review', () => {
  equal(reviewDeliveryArea([area, {...area, latitude:10.95}], reverse, scope), 'AMBIGUOUS_PIN_LOCALITY');
  equal(reviewDeliveryArea([{...area, locality:'Different Ward'}], reverse, scope), 'PIN_LOCALITY_UNCONFIRMED');
  equal(reviewDeliveryArea(null, reverse, scope), 'INVALID_EVIDENCE_INPUT');
});

test('reviews in-memory evidence from stdin and prints only the verdict', () => {
  const run = places => spawnSync(process.execPath, [join(__dirname, 'Review-PilotLocation.cjs'), '-',
    scope.pin, String(scope.latitude), String(scope.longitude), scope.city, scope.state],
    {input:JSON.stringify({places, reverse}),encoding:'utf8'});
  const consistent = run([area]);
  equal(consistent.status, 0);
  equal(consistent.stdout.trim(), 'CONSISTENT_FOR_HUMAN_REVIEW');
  const ambiguous = run([area, {...area, latitude:10.95}]);
  equal(ambiguous.status, 1);
  equal(ambiguous.stdout.trim(), 'AMBIGUOUS_PIN_LOCALITY');
});
