import assert from 'node:assert/strict';
import {test} from 'node:test';
import {includePacks,integerField,planExpiry,weeklyBudget} from '../src/services/weekly-shopping-input';

test('importing exact cart packs keeps manual quantities and preserves distinct variants',()=>{
 assert.deepEqual(includePacks([{productId:'rice-500',quantity:2}],[
  {productId:'rice-500',quantity:1},{productId:'rice-1000',quantity:3},{productId:'rice-1000',quantity:5}
 ]),[{productId:'rice-500',quantity:2},{productId:'rice-1000',quantity:3}]);
 assert.equal(includePacks([],Array.from({length:40},(_,i)=>({productId:String(i),quantity:1}))).length,30);
});

test('weekly controls distinguish an unset budget and reject invalid quantities',()=>{
 assert.equal(weeklyBudget(''),null);
 assert.equal(weeklyBudget('0'),0);
 assert.equal(weeklyBudget('550.05'),55005);
 assert.throws(()=>weeklyBudget('12.001'));
 assert.throws(()=>integerField('2.5',7,'day'));
 assert.throws(()=>integerField('8',7,'day'));
});

test('a comparison expires with the oldest included quote',()=>{
 assert.equal(planExpiry(['2026-11-15T10:06:00Z','2026-11-15T10:03:00Z'],'2026-11-15T10:10:00Z'),
  Date.parse('2026-11-15T10:18:00Z'));
 assert.equal(planExpiry([],'2026-11-15T10:10:00Z'),Date.parse('2026-11-15T10:25:00Z'));
 assert.ok(Number.isNaN(planExpiry(['invalid'],'2026-11-15T10:10:00Z')));
});
