const { expect } = require('playwright/test');

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

module.exports = { checked, adminHeaders, cleanupUserRole };
