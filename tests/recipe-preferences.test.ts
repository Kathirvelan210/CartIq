import {test} from 'node:test';
import assert from 'node:assert/strict';
import {recipeRequest} from '../src/services/recipe-preferences';

const base={idea:'tomato rice',servings:'2',cuisine:'South Indian',meal:'DINNER',diet:'VEGETARIAN',difficulty:'EASY',minutes:'30',budget:'200'};
test('recipe generation carries all selected preferences and quantities into one request',()=>{
 const prompt=recipeRequest(base);
 for(const value of ['tomato rice','Servings: 2','south indian','dinner','vegetarian','easy','30 minutes','INR 200'])assert.ok(prompt.includes(value));
 assert.ok(recipeRequest({...base,idea:''}).includes('Suggest a recipe'));
});
test('recipe input rejects invalid limits before a model call',()=>{
 for(const change of [{servings:'0'},{servings:'21'},{minutes:'-1'},{budget:'-100'},{idea:'a'.repeat(601)}])
  assert.throws(()=>recipeRequest({...base,...change}));
});
