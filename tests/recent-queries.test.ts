import {test} from 'node:test';
import assert from 'node:assert/strict';
import {clearRecentQueries,comparisonBookmark,recentQueries,rememberQuery,saveComparisonBookmark} from '../src/services/recent-queries';

test('recent browser searches survive reload, deduplicate and clear per account', () => {
  const map = new Map<string,string>();
  const storage = {getItem:(k:string)=>map.get(k)??null,setItem:(k:string,v:string)=>map.set(k,v),removeItem:(k:string)=>map.delete(k)};
  for (const term of ['milk','rice','Milk']) rememberQuery(storage,'a',term,recentQueries(storage,'a'));
  assert.deepEqual(recentQueries(storage,'a'),['Milk','rice']);
  assert.deepEqual(recentQueries(storage,'b'),[]);
  clearRecentQueries(storage,'a');
  assert.deepEqual(recentQueries(storage,'a'),[]);
  const id = '00000000-0000-4000-a000-000000000001';
  saveComparisonBookmark(storage,'a','place1 milk',id,Date.now()+30000);
  assert.equal(comparisonBookmark(storage,'a','place1 milk'),id);
  assert.equal(comparisonBookmark(storage,'a','place2 milk'),undefined);
  assert.equal(comparisonBookmark(storage,'b','place1 milk'),undefined);
  saveComparisonBookmark(storage,'a','place1 milk',id,Date.now()-1);
  assert.equal(comparisonBookmark(storage,'a','place1 milk'),undefined);
});
