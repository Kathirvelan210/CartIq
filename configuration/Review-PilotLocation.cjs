const {readFileSync} = require('node:fs');

function normalize(value) {
  return typeof value === 'string'
    ? value.normalize('NFKC').trim().replace(/\s+/g, ' ').toLowerCase()
    : '';
}

function reviewDeliveryArea(places, reverse, {pin, latitude, longitude, city, state}) {
  const expectedCity = normalize(city);
  const expectedState = normalize(state);
  if (!/^[1-9][0-9]{5}$/.test(pin) || !expectedCity || !expectedState ||
      !Number.isFinite(latitude) || Math.abs(latitude) > 90 ||
      !Number.isFinite(longitude) || Math.abs(longitude) > 180 || !Array.isArray(places)) {
    return 'INVALID_EVIDENCE_INPUT';
  }
  const matchesArea = place => normalize(place?.countryCode) === 'in' && place.postcode === pin &&
    normalize(place.city) === expectedCity && normalize(place.state) === expectedState &&
    normalize(place.locality) && normalize(place.locality) !== expectedCity;
  if (!matchesArea(reverse) || reverse.approximate !== false ||
      reverse.latitude !== latitude || reverse.longitude !== longitude) {
    return 'REVERSE_AREA_UNCONFIRMED';
  }
  const reverseDistrict = normalize(reverse.district);
  const matches = places.filter(place => {
    if (!matchesArea(place) || normalize(place.locality) !== normalize(reverse.locality)) return false;
    const district = normalize(place.district);
    return !district || !reverseDistrict || district === reverseDistrict;
  });
  if (matches.length !== 1) return matches.length ? 'AMBIGUOUS_PIN_LOCALITY' : 'PIN_LOCALITY_UNCONFIRMED';
  return 'CONSISTENT_FOR_HUMAN_REVIEW';
}

if (require.main === module) {
  const args = process.argv.slice(2);
  let result = 'INVALID_EVIDENCE_INPUT';
  const readJson = file => JSON.parse(readFileSync(file, 'utf8').replace(/^\uFEFF/, ''));
  try {
    let places, reverse, scope;
    if (args.length === 6 && args[0] === '-') {
      ({places, reverse} = readJson(0));
      scope = args.slice(1);
    } else if (args.length === 7) {
      places = readJson(args[0]);
      reverse = readJson(args[1]);
      scope = args.slice(2);
    }
    if (scope) {
      const [pin, latitude, longitude, city, state] = scope;
      result = reviewDeliveryArea(places, reverse, {pin, latitude: latitude.trim() ? Number(latitude) : NaN,
        longitude: longitude.trim() ? Number(longitude) : NaN, city, state});
    }
  } catch {
    // Report only the status. Evidence may contain sensitive location details.
  }
  console.log(result);
  process.exitCode = result === 'CONSISTENT_FOR_HUMAN_REVIEW' ? 0 : 1;
}

module.exports = {reviewDeliveryArea};
