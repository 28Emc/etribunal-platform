import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');

// Test configuration
export const options = {
  stages: [
    { duration: '30s', target: 50 },   // Ramp up to 50 VUs
    { duration: '1m', target: 100 },   // Ramp up to 100 VUs
    { duration: '2m', target: 100 },   // Stay at 100 VUs
    { duration: '30s', target: 200 },  // Spike to 200 VUs
    { duration: '1m', target: 200 },   // Stay at 200 VUs
    { duration: '30s', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
    errors: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';
const FRONTEND_URL = __ENV.FRONTEND_URL || 'http://localhost:3000';

// Test data
const testUsers = [];
const NUM_TEST_USERS = 50;

function generateUser(i) {
  const username = `loadtest_user_${i}_${Date.now()}`;
  return {
    username,
    email: `${username}@loadtest.com`,
    password: 'Password123',
    displayName: `LoadTest User ${i}`,
  };
}

export function setup() {
  // Register test users and get tokens
  const users = [];
  for (let i = 0; i < NUM_TEST_USERS; i++) {
    const user = generateUser(i);
    const registerRes = http.post(`${BASE_URL}/auth/register`, JSON.stringify(user), {
      headers: { 'Content-Type': 'application/json' },
    });
    if (registerRes.status === 201) {
      const data = registerRes.json('data');
      users.push({ ...user, accessToken: data.access_token, refreshToken: data.refresh_token, id: data.user.id });
    }
  }
  console.log(`Registered ${users.length} test users`);
  return { users };
}

export default function (data) {
  if (!data.users || data.users.length === 0) {
    console.log('No test users available, skipping');
    return;
  }

  const user = data.users[Math.floor(Math.random() * data.users.length)];
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${user.accessToken}`,
  };

  // Scenario weights
  const scenario = Math.random();
  
  if (scenario < 0.4) {
    // 40% - Browse feed (GET /cases)
    browseFeed(headers);
  } else if (scenario < 0.6) {
    // 20% - View case detail (GET /cases/{id})
    viewCaseDetail(headers);
  } else if (scenario < 0.75) {
    // 15% - Vote on case (POST /cases/{id}/votes)
    voteOnCase(headers);
  } else if (scenario < 0.85) {
    // 10% - Add comment (POST /cases/{id}/comments)
    addComment(headers);
  } else if (scenario < 0.9) {
    // 5% - React to case (POST /reactions)
    reactToCase(headers);
  } else if (scenario < 0.95) {
    // 5% - Save/Unsave case (POST /saved-cases/{id}/save)
    toggleSave(headers);
  } else {
    // 5% - Search (GET /cases/search)
    searchCases(headers);
  }

  sleep(Math.random() * 2 + 0.5); // 0.5-2.5s think time
}

function browseFeed(headers) {
  const feedTypes = ['for_you', 'trending', 'following'];
  const feedType = feedTypes[Math.floor(Math.random() * feedTypes.length)];
  
  const res = http.get(`${BASE_URL}/cases?feedType=${feedType}&skip=0&take=20`, { headers });
  const success = check(res, {
    'feed status 200': (r) => r.status === 200,
    'feed has cases': (r) => r.json().data.length >= 0,
    'feed response time < 500ms': (r) => r.timings.duration < 500,
  });
  errorRate.add(!success);
}

function viewCaseDetail(headers) {
  // Get a random case ID from feed first
  const feedRes = http.get(`${BASE_URL}/cases?skip=0&take=10`, { headers });
  if (feedRes.status !== 200 || feedRes.json().data.length === 0) return;
  
  const cases = feedRes.json().data;
  const caseId = cases[Math.floor(Math.random() * cases.length)].id;
  
  const res = http.get(`${BASE_URL}/cases/${caseId}`, { headers });
  const success = check(res, {
    'case detail status 200': (r) => r.status === 200,
    'case has content': (r) => r.json().data.side_a_content !== undefined,
    'case detail response time < 500ms': (r) => r.timings.duration < 500,
  });
  errorRate.add(!success);
}

function voteOnCase(headers) {
  const feedRes = http.get(`${BASE_URL}/cases?skip=0&take=20`, { headers });
  if (feedRes.status !== 200 || feedRes.json().data.length === 0) return;
  
  const cases = feedRes.json().data;
  const voteCase = cases[Math.floor(Math.random() * cases.length)];
  const voteTypes = ['A', 'B', 'BOTH_WRONG'];
  const voteType = voteTypes[Math.floor(Math.random() * voteTypes.length)];
  
  const res = http.post(`${BASE_URL}/cases/${voteCase.id}/votes`, JSON.stringify({ vote_type: voteType }), { headers });
  const success = check(res, {
    'vote status 200 or 400 (already voted)': (r) => r.status === 200 || r.status === 400,
    'vote response time < 300ms': (r) => r.timings.duration < 300,
  });
  errorRate.add(!success);
}

function addComment(headers) {
  const feedRes = http.get(`${BASE_URL}/cases?skip=0&take=10`, { headers });
  if (feedRes.status !== 200 || feedRes.json().data.length === 0) return;
  
  const cases = feedRes.json().data;
  const caseId = cases[Math.floor(Math.random() * cases.length)].id;
  
  const comments = [
    'Interesante punto de vista',
    'No estoy de acuerdo',
    'Buen argumento',
    'Me parece bien',
    'Tienes razón',
  ];
  const content = comments[Math.floor(Math.random() * comments.length)];
  
  const res = http.post(`${BASE_URL}/cases/${caseId}/comments`, JSON.stringify({ content }), { headers });
  const success = check(res, {
    'comment status 200 or 201': (r) => r.status === 200 || r.status === 201,
    'comment response time < 300ms': (r) => r.timings.duration < 300,
  });
  errorRate.add(!success);
}

function reactToCase(headers) {
  const feedRes = http.get(`${BASE_URL}/cases?skip=0&take=10`, { headers });
  if (feedRes.status !== 200 || feedRes.json().data.length === 0) return;
  
  const cases = feedRes.json().data;
  const caseId = cases[Math.floor(Math.random() * cases.length)].id;
  const emojis = ['LIKE', 'LOVE', 'ANGRY'];
  const emoji = emojis[Math.floor(Math.random() * emojis.length)];
  
  const res = http.post(`${BASE_URL}/reactions`, JSON.stringify({ 
    target_type: 'CASE', 
    target_id: caseId, 
    emoji 
  }), { headers });
  const success = check(res, {
    'reaction status 200 or 201': (r) => r.status === 200 || r.status === 201,
    'reaction response time < 300ms': (r) => r.timings.duration < 300,
  });
  errorRate.add(!success);
}

function toggleSave(headers) {
  const feedRes = http.get(`${BASE_URL}/cases?skip=0&take=10`, { headers });
  if (feedRes.status !== 200 || feedRes.json().data.length === 0) return;
  
  const cases = feedRes.json().data;
  const caseId = cases[Math.floor(Math.random() * cases.length)].id;
  
  const res = http.post(`${BASE_URL}/saved-cases/${caseId}/save`, null, { headers });
  const success = check(res, {
    'save status 200': (r) => r.status === 200,
    'save response time < 300ms': (r) => r.timings.duration < 300,
  });
  errorRate.add(!success);
}

function searchCases(headers) {
  const queries = ['divorcio', 'trabajo', 'familia', 'dinero', 'casa'];
  const q = queries[Math.floor(Math.random() * queries.length)];
  
  const res = http.get(`${BASE_URL}/cases/search?q=${q}&skip=0&take=8`, { headers });
  const success = check(res, {
    'search status 200': (r) => r.status === 200,
    'search response time < 500ms': (r) => r.timings.duration < 500,
  });
  errorRate.add(!success);
}

export function teardown(data) {
  console.log('Load test completed');
  if (data.users) {
    console.log(`Tested with ${data.users.length} users`);
  }
}