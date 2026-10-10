const { test: base, expect, request: playwrightRequest } = require('playwright/test');

async function checked(response) {
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  expect(body.code).toBe(200);
  return body.data;
}

async function adminHeaders(request) {
  const data = await checked(await request.post('/api/auth/login', {
    data: { username: 'admin', password: 'admin123' },
  }));
  return { Authorization: `Bearer ${data.token}` };
}

// Exact ownership checks prevent cleanup from touching seed or unrelated records.
// Discover by unique key so cleanup also works after a failed create/read assertion.
async function cleanupUserRole(request, headers, username, roleKey) {
  const failures = [];
  for (const [resource, field, value, id] of [
    ['user', 'user_name', username, 'user_id'],
    ['role', 'role_key', roleKey, 'role_id'],
  ]) {
    if (!value) continue;
    try {
      const data = await checked(await request.get(`/api/system/${resource}`, {
        headers, params: { [field]: value, page: 1, size: 100 },
      }));
      for (const row of data.rows.filter(row => row[field] === value)) {
        await checked(await request.delete(`/api/system/${resource}/${row[id]}`, { headers }));
      }
      const remaining = await checked(await request.get(`/api/system/${resource}`, {
        headers, params: { [field]: value, page: 1, size: 100 },
      }));
      expect(remaining.rows.filter(row => row[field] === value)).toEqual([]);
    } catch (error) { failures.push(error); }
  }
  try { await checked(await request.post('/api/auth/logout', { headers })); }
  catch (error) { failures.push(error); }
  if (failures.length) throw new AggregateError(failures, 'User/role cleanup failed');
}

// Fixture teardown gets its own timeout and API lifecycle even if the UI test times out.
const test = base.extend({
  userRoleCleanup: [async ({ baseURL }, use, testInfo) => {
    const records = [];
    await use((headers, username, roleKey) => records.push({ headers, username, roleKey }));
    if (!records.length) return;
    const request = await playwrightRequest.newContext({ baseURL, timeout: 10000 });
    const errors = [];
    try {
      for (const { headers, username, roleKey } of records) {
        try { await cleanupUserRole(request, headers, username, roleKey); }
        catch (error) { errors.push(error); }
      }
    } finally { await request.dispose(); }
    if (errors.length) {
      const details = errors.flatMap(error => error.errors || [error])
        .map(error => error.stack || String(error)).join('\n\n');
      await testInfo.attach('user-role-cleanup-errors', { body: details, contentType: 'text/plain' });
      // Keep the original UI failure as the primary diagnosis.
      if (testInfo.status === testInfo.expectedStatus) {
        throw new AggregateError(errors, 'User/role cleanup failed');
      }
    }
  }, { timeout: 60000 }],
});

module.exports = { test, checked, adminHeaders, cleanupUserRole };
